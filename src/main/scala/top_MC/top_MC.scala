/*
RISC-V Pipelined Project in Chisel

This project implements a pipelined RISC-V processor in Chisel. The pipeline includes five stages: fetch, decode, execute, memory, and writeback.
The core is part of an educational project by the Chair of Electronic Design Automation (https://eit.rptu.de/fgs/eis/) at RPTU Kaiserslautern, Germany.

Supervision and Organization: Tobias Jauch, Philipp Schmitz, Alex Wezel
Student Workers: Giorgi Solomnishvili, Zahra Jenab Mahabadi, Tsotne Karchava, Abdullah Shaaban Saad Allam, Guga Kupradze

*/

package top_MC

import chisel3._
import chisel3.util._
import Piplined_RISC_V._
import Stage_ID.ID
import Stage_IF.IF
import Stage_EX.EX
import Stage_MEM.MEM
import HazardUnit.HazardUnit
import config.{MemUpdates,Instruction, RegisterUpdates, TestReadouts}
import combined_memory.combined_memory
import DCache.DCache
import DCache.CacheAndMemory
class top_MC(BinaryFile: String) extends Module {

  val testHarness = IO(
    new Bundle {
     //val setupSignals = Input(new SetupSignals)
      val testReadouts = Output(new TestReadouts)
      val regUpdates   = Output(new RegisterUpdates)
      val memUpdates   = Output(new MemUpdates)
      val currentPC    = Output(UInt(32.W))
      //val PC        = Output(UInt())
    }
  )

  val combined = Module(new combined_memory(BinaryFile))

  
  // Pipeline Registers
  val IFBarrier  = Module(new IFpipe).io
  val IDBarrier  = Module(new IDpipe).io
  val EXBarrier  = Module(new EXpipe).io
  val MEMBarrier = Module(new MEMpipe).io

 // Pipeline Stages
  val IF  = Module(new IF(BinaryFile))
  val ID  = Module(new ID)
  val EX  = Module(new EX)
  val MEM = Module(new MEM)
  val writeBackData = Wire(UInt())
  var dcache = Module(new DCache("src/main/scala/DCache/CacheContent.bin"))
  var CM = Module(new CacheAndMemory)

  // Hazard Unit
  val HzdUnit = Module(new HazardUnit)

  
  


  combined.io.instructionAddress := IF.io.fetchPC 
  IF.io.instruction_inp := combined.io.instruction.asTypeOf(new Instruction)

  combined.io.writeEnable := dcache.io.mem_write_en
  combined.io.readEnable := dcache.io.mem_read_en
  combined.io.dataIn := dcache.io.mem_data_in
  combined.io.dataAddress := dcache.io.mem_data_addr / 4.U
  dcache.io.mem_data_out := combined.io.dataOut


  dcache.io.data_in := CM.io.cache_data_in 
  dcache.io.data_addr := CM.io.cache_data_addr
  dcache.io.write_en := CM.io.cache_write_en
  dcache.io.read_en := CM.io.cache_read_en
  CM.io.cache_valid := dcache.io.valid
  CM.io.cache_data_out := dcache.io.data_out 
  CM.io.cache_busy := dcache.io.busy

  CM.io.write_data  := MEM.io.CM_write_data
  CM.io.address := MEM.io.CM_address
  CM.io.write_en := MEM.io.CM_write_en
  CM.io.read_en := MEM.io.CM_read_en
  
  MEM.io.CM_data_out := CM.io.data_out
  MEM.io.CM_dataValid := CM.io.valid
  MEM.io.CM_memBusy := CM.io.busy

  
  //IF.testHarness.InstructionMemorySetup := testHarness.setupSignals.IMEMsignals
  //  ID.testHarness.registerSetup          := testHarness.setupSignals.registerSignals
  //  MEM.testHarness.DMEMsetup             := testHarness.setupSignals.DMEMsignals

 testHarness.testReadouts.registerRead := ID.testHarness.registerPeek
//  testHarness.testReadouts.DMEMread     := MEM.testHarness.DMEMpeek
 testHarness.testReadouts.DMEMread     := combined.io.dataOut


   testHarness.regUpdates                := ID.testHarness.testUpdates
  testHarness.memUpdates                := MEM.testHarness.testUpdates
   testHarness.currentPC                 := combined.io.instructionAddress


  // Fetch Stage
  IF.io.branchTaken        := EX.io.branchTaken
  IF.io.IFBarrierPC        := IFBarrier.outCurrentPC
  IF.io.stall              := HzdUnit.io.stall | HzdUnit.io.stall_membusy     // Stall Fetch -> PC_en=0
  IF.io.newBranch          := EX.io.newBranch
  IF.io.updatePrediction   := EX.io.updatePrediction
  IF.io.entryPC            := IDBarrier.outPC
  IF.io.branchAddr         := EX.io.branchTarget
  IF.io.branchMispredicted := HzdUnit.io.branchMispredicted
  IF.io.PCplus4ExStage     := EX.io.outPCplus4

  //Signals to IFBarrier
  IFBarrier.inCurrentPC        := IF.io.PC
  IFBarrier.inInstruction      := IF.io.instruction
  IFBarrier.stall              := HzdUnit.io.stall | HzdUnit.io.stall_membusy     // Stall Decode -> IFBarrier_en=0
  IFBarrier.flush              := HzdUnit.io.flushD
  IFBarrier.inBTBHit           := IF.io.btbHit
  IFBarrier.inBTBPrediction    := IF.io.btbPrediction
  IFBarrier.inBTBTargetPredict := IF.io.btbTargetPredict

  //Decode stage
  ID.io.instruction           := IFBarrier.outInstruction
  ID.io.registerWriteAddress  := MEMBarrier.outRd
  ID.io.registerWriteEnable   := MEMBarrier.outControlSignals.regWrite

  //Signals to IDBarrier
  IDBarrier.inInstruction      := ID.io.instruction
  IDBarrier.inControlSignals   := ID.io.controlSignals
  IDBarrier.inBranchType       := ID.io.branchType
  IDBarrier.inPC               := IFBarrier.outCurrentPC
  IDBarrier.flush              := HzdUnit.io.flushE
  IDBarrier.stall              := HzdUnit.io.stall_membusy
  IDBarrier.inOp1Select        := ID.io.op1Select
  IDBarrier.inOp2Select        := ID.io.op2Select
  IDBarrier.inImmData          := ID.io.immData
  IDBarrier.inRd               := IFBarrier.outInstruction.registerRd
  IDBarrier.inALUop            := ID.io.ALUop
  IDBarrier.inReadData1        := ID.io.readData1
  IDBarrier.inReadData2        := ID.io.readData2
  IDBarrier.inBTBHit           := IFBarrier.outBTBHit
  IDBarrier.inBTBPrediction    := IFBarrier.outBTBPrediction
  IDBarrier.inBTBTargetPredict := IFBarrier.outBTBTargetPredict

  //Execute stage
  EX.io.instruction           := IDBarrier.outInstruction
  EX.io.controlSignals        := IDBarrier.outControlSignals
  EX.io.PC                    := IDBarrier.outPC
  EX.io.branchType            := IDBarrier.outBranchType
  EX.io.op1Select             := IDBarrier.outOp1Select
  EX.io.op2Select             := IDBarrier.outOp2Select
  EX.io.rs1Select             := HzdUnit.io.rs1Select
  EX.io.rs2Select             := HzdUnit.io.rs2Select
  EX.io.rs1                   := IDBarrier.outReadData1
  EX.io.rs2                   := IDBarrier.outReadData2
  EX.io.immData               := IDBarrier.outImmData
  EX.io.ALUop                 := IDBarrier.outALUop
  EX.io.ALUresultEXB          := EXBarrier.outALUResult
  EX.io.ALUresultMEMB         := writeBackData
  EX.io.btbHit                := IDBarrier.outBTBHit
  EX.io.btbTargetPredict      := IDBarrier.outBTBTargetPredict

  // Hazard Unit
  HzdUnit.io.controlSignalsEXB  := EXBarrier.outControlSignals
  HzdUnit.io.controlSignalsMEMB := MEMBarrier.outControlSignals
  HzdUnit.io.rs1AddrIFB         := IFBarrier.outInstruction.registerRs1
  HzdUnit.io.rs2AddrIFB         := IFBarrier.outInstruction.registerRs2
  HzdUnit.io.rs1AddrIDB         := IDBarrier.outInstruction.registerRs1
  HzdUnit.io.rs2AddrIDB         := IDBarrier.outInstruction.registerRs2
  HzdUnit.io.rdAddrIDB          := IDBarrier.outInstruction.registerRd
  HzdUnit.io.rdAddrEXB          := EXBarrier.outRd
  HzdUnit.io.rdAddrMEMB         := MEMBarrier.outRd
  HzdUnit.io.branchTaken        := EX.io.branchTaken
  HzdUnit.io.wrongAddrPred      := EX.io.wrongAddrPred
  HzdUnit.io.btbPrediction      := IDBarrier.outBTBPrediction
  HzdUnit.io.branchType         := IDBarrier.outBranchType
  HzdUnit.io.membusy            := MEM.io.memBusy

  //Signals to EXBarrier
  EXBarrier.inALUResult       := EX.io.ALUResult
  EXBarrier.inControlSignals  := IDBarrier.outControlSignals
  EXBarrier.inRd              := IDBarrier.outRd
  EXBarrier.inRs2             := EX.io.Rs2Forwarded
  EXBarrier.stall             := HzdUnit.io.stall_membusy

  //MEM stage
  MEM.io.dataIn               := EXBarrier.outRs2
  MEM.io.dataAddress          := EXBarrier.outALUResult
  MEM.io.writeEnable          := EXBarrier.outControlSignals.memWrite
  MEM.io.readEnable           := EXBarrier.outControlSignals.memRead

  //MEMBarrier
  MEMBarrier.inControlSignals := EXBarrier.outControlSignals
  MEMBarrier.inALUResult      := EXBarrier.outALUResult
  MEMBarrier.inRd             := EXBarrier.outRd
  MEMBarrier.inMEMData        := MEM.io.dataOut
  MEMBarrier.stall            := HzdUnit.io.stall_membusy




  // MEM stage
  //Mux for which data to write to register
  when(MEMBarrier.outControlSignals.memToReg){
    writeBackData := MEMBarrier.outMEMData
  }.otherwise{
    writeBackData := MEMBarrier.outALUResult
  }

  ID.io.registerWriteData := writeBackData
}