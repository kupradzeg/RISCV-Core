/*
RISC-V Pipelined Project in Chisel

This project implements a pipelined RISC-V processor in Chisel. The pipeline includes five stages: fetch, decode, execute, memory, and writeback.
The core is part of an educational project by the Chair of Electronic Design Automation (https://eit.rptu.de/fgs/eis/) at RPTU Kaiserslautern, Germany.

Supervision and Organization: Tobias Jauch, Philipp Schmitz, Alex Wezel
Student Workers: Giorgi Solomnishvili, Zahra Jenab Mahabadi, Tsotne Karchava, Abdullah Shaaban Saad Allam.

*/

package Branch_OP
import config.branch_types._
import chisel3._
import chisel3.util._
class Branch_OP extends Module {
  val io = IO(
    new Bundle {
      val branchType  = Input(UInt())
      val src1        = Input(UInt())
      val src2        = Input(UInt())
      val branchTaken = Output(UInt())
    }
  )

  //Branch lookup
  io.branchTaken := 0.U

  //Convert source values to signed integers for comparison
  val lhs = io.src1.asSInt
  val rhs = io.src2.asSInt


  switch(io.branchType) {
    is(beq) { //Branch if equal
      io.branchTaken := (lhs === rhs)
    }
    is(neq) { //Branch if not equal
      io.branchTaken := (lhs =/= rhs)
    }
    is(gte) { //Branch if greater than or equal
      io.branchTaken := (lhs >= rhs)
    }
    is(lt) { //Branch if less than
      io.branchTaken := (lhs < rhs)
    }
    is(gteu) { //Branch if greater than or equal (Unsigned)
      io.branchTaken := (lhs >= rhs)
    }
    is(ltu) { //Branch if less than (unsigned)
      io.branchTaken := (lhs < rhs)
    }
    is(jump) { //Unconditional Jump
      io.branchTaken := (1.U)
    }
    is(DC) { //Don't care, No branch
      io.branchTaken := (0.U)
    }
  }


}
