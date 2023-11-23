package combine

import chisel3._
import chisel3.util._
import config.IMEMsetupSignals

import chisel3.experimental.{ChiselAnnotation, annotate}
import firrtl.annotations.MemorySynthInit
import config.DMEMsetupSignals
import config.MemUpdates
import chisel3.experimental.{ChiselAnnotation, annotate}
import chisel3.util.experimental.loadMemoryFromFileInline
import firrtl.annotations.MemorySynthInit
import firrtl.annotations.{Annotation, MemorySynthInit}

class combine(I_memoryFile: String, D_memoryFile: String = "src/main/scala/DataMemory/dataMemVals") extends Module //default constructor
{
  val testHarness = IO(
    new Bundle {
      val setup = Input(new DMEMsetupSignals)
      val setupSignals = Input(new IMEMsetupSignals) 
      val requestedAddress = Output(UInt())
      val testUpdates = Output(new MemUpdates)
    })


  val io = IO(
    new Bundle {
      val writeEnable = Input(Bool())
      val readEnable  = Input(Bool())
      val dataIn      = Input(UInt(32.W))
      val dataAddress = Input(UInt(32.W))

      val dataOut     = Output(UInt(32.W))
      val instructionAddress = Input(UInt(32.W))
      val instruction        = Output(UInt(32.W))
    })
  //Annotation for synthesizing memory initialization
  annotate(new ChiselAnnotation {
    override def toFirrtl = MemorySynthInit
  })

  // val d_memory = SyncReadMem(4096, UInt(32.W))  //changed to 16,384
  // val d_memory = SyncReadMem(16384, UInt(32.W))  //changed to 16,384
  val i_memory = SyncReadMem(4096, UInt(32.W))
  loadMemoryFromFileInline(i_memory,I_memoryFile)
  val d_memory = SyncReadMem(1048576, UInt(32.W))  //changed to   524288
  
  loadMemoryFromFileInline(d_memory,D_memoryFile)


  val addressSource = Wire(UInt(32.W))
  val dataSource = Wire(UInt(32.W))
  val writeEnableSource = Wire(Bool())
  val readEnableSource = Wire(Bool())

  val addressSource2 = Wire(UInt(32.W))
  testHarness.requestedAddress := io.instructionAddress

  // For loading data
  when(testHarness.setup.setup){
    addressSource     := testHarness.setup.dataAddress
    dataSource        := testHarness.setup.dataIn
    writeEnableSource := testHarness.setup.writeEnable
    readEnableSource  := testHarness.setup.readEnable

  }.otherwise {
    addressSource     := io.dataAddress
    dataSource        := io.dataIn
    writeEnableSource := io.writeEnable
    readEnableSource  := io.readEnable
  }

  when(testHarness.setupSignals.setup){
    addressSource2 := testHarness.setupSignals.address
  }.otherwise {
    addressSource2 := io.instructionAddress
  }

  
  //Output updates to the test harness
  testHarness.testUpdates.writeEnable  := writeEnableSource
  testHarness.testUpdates.readEnable   := readEnableSource
  testHarness.testUpdates.writeData    := dataSource
  testHarness.testUpdates.writeAddress := addressSource


  when(testHarness.setupSignals.setup){
    i_memory(addressSource2) := testHarness.setupSignals.instruction
  }

  //Memory write operation based on write enable signal
  when(writeEnableSource){
    d_memory(addressSource) := dataSource
  }

  // io.dataOut := Mux(readEnableSource, d_memory(addressSource), 0.U(32.W))
  io.dataOut := d_memory(addressSource)
  io.instruction := i_memory(addressSource2(31,2))
}