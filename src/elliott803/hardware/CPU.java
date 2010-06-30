/**
 * Elliott Model 803B Simulator
 *
 * (C) Copyright Tim Baldwin 2009
 */
package elliott803.hardware;

import elliott803.machine.Computer;
import elliott803.machine.Dump;
import elliott803.machine.Instruction;
import elliott803.machine.Trace;
import elliott803.machine.Word;
import elliott803.view.CpuView;

/**
 * This is the Central Processor.  This fetches and executes instructions.
 *
 * @author Baldwin
 */
public class CPU {

    public Computer computer;   // The owning computer

    // CPU registers and flags
    long acc;               // Accumulator register
    long ar;                // Auxiliary register
    long br;                // B-line modification value
    boolean overflow;       // Overflow flag
    boolean fpOverflow;     // Floating point overflow

    long ir;                // Instruction register
    int scr;                // Sequence control register
    int scr2;               // 0 = first instruction, 1 = second instruction

    // Variables used during execution
    boolean jump;
    boolean running;
    Trace trace;

    public CPU(Computer computer) {
        this.computer = computer;
    }
    
    // Set the next instruction to be executed - only the first instruction
    // can be set.
    public void setInstruction(int instruction) {
        ir = Word.asInstr1(instruction);
        scr2 = 0;
        viewState();
    }

    // Reset the CPU - clears overflow and busy states and stops execution
    public void reset() {
        stop();
        overflow = fpOverflow = false;
        computer.console.setOverflow(overflow, fpOverflow);
        computer.busyClear();
    }

    // Stop execution
    public void stop() {
        computer.console.setStep(true);
        running = false;
        jump = true;
    }

    // Normal execution, run instructions until told to stop.
    public void run() {
        computer.console.setStep(false);
        running = true;
        while (running) {
            obey();
        }
    }

    // Obey the next instruction from the instruction register.  The scr2 register
    // will indicate if we should execute the first or second instruction of the pair.
    public void obey() {
        if (trace != null) {
            // Only trace following a jump or starting a new pair of instructions
            if (jump || scr2 == 0)
                trace.trace(scr, scr2, ir, acc, overflow);
        }    
        
        // Get the instruction to be executed and apply any B-digit modification
        // if executing the second instruction.  A jump to the second instruction
        // does not do any B-digit modification.
        int instruction;
        if (scr2 == 0) {
            instruction = Word.getInstr1(ir);
        } else {
            instruction = Word.getInstr2(ir);
            if (Word.getB(ir) == 1 && !jump) {
                instruction += Word.getInstr2(br);
            }
        }
        
        // Execute the instruction and store any potential B-line modification
        long n = execute(instruction);
        br = (scr2 == 0) ? n : 0;
        
        // Step to the next instruction, unless we had jump in which case the 
        // new address will already be set in scr/scr2.
        if (!jump) {
            if (scr2 == 0) {
                scr2 = 1;
            } else {
                scr2 = 0;
                scr = Instruction.getAddr(scr + 1);
            }
        }

        // Fetch the next instruction and display the current register state
        ir = computer.core.fetch(scr);

        viewState();
    }

    // Execute a single instruction
    long execute(int instruction) {
        int op = Instruction.getOp(instruction);
        int addr = Instruction.getAddr(instruction);

        // Perform the operation
        jump = false;
        switch (op >> 3) {
            case 0: case 1: case 2: 
            case 3: group0123(op, addr);  break;
            case 4: group4(op, addr);  break;
            case 5: group5(op, addr);  break;
            case 6: group6(op, addr);  break;
            case 7: group7(op, addr);  break;
        }

        // Update console lights to track overflow states
        computer.console.setOverflow(overflow, fpOverflow);
        if (fpOverflow) {
            // Floating point overflow should wait for Operate before continuing
            computer.console.suspend();
            fpOverflow = false;
            computer.console.setOverflow(overflow, fpOverflow);
        }
        
        // Return the final value from the storage address
        return computer.core.read(addr);
    }

    // Execute a group 0 to group 3 arithmetic/storage instruction
    void group0123(int op, int addr) {
        // We need the current content of the storage address and, for some instructions
        // either the store value or the accumulator.
        long a = acc;
        long n = computer.core.read(addr);
        long x = ((op & 010) == 0) ? a : n;

        // Calculate the result
        long result = 0;
        switch (op & 007) {
            case 0: result = computer.alu.add(0, x);  break;    // Do nothing
            case 1: result = computer.alu.sub(0, x);  break;    // Negate
            case 2: result = computer.alu.add(1, n);  break;    // Increment
            case 3: result = computer.alu.and(a, n);  break;    // Collate
            case 4: result = computer.alu.add(a, n);  break;    // Add
            case 5: result = computer.alu.sub(a, n);  break;    // Subtract
            case 6: result = computer.alu.add(0, 0);  break;    // Clear
            case 7: result = computer.alu.sub(n, a);  break;    // Negate and add
        }
        overflow |= computer.alu.isOverflow();

        // Destination of result depends on opcode group
        switch (op >> 3) {
            case 0: acc = result;  x = n;  break;
            case 1: acc = result;  x = a;  break;
            case 2: acc = a;  x = result;  break;
            case 3: acc = n;  x = result;  break;
        }
        computer.core.write(addr, x);
    }

    // Execute a group 4 jump instruction
    void group4(int op, int addr) {
        switch (op & 003) {
            case 0: jump = true;  break;                        // Unconditional
            case 1: jump = computer.alu.isNeg(acc);  break;     // Jump if accumulator negative
            case 2: jump = computer.alu.isZero(acc);  break;    // Jump if accumulator zero
            case 3: jump = overflow;  overflow = false;  break; // Jump if overflow (and clear)
        }

        if (jump) {                         // Jump required
            scr = addr;                     //   set new sequence control address
            scr2 = (op & 007) >> 2;         //   and set first/second instruction indication
        }
    }

    // Execute a group5 logical/arithmetic instruction
    void group5(int op, int addr) {
        long n = computer.core.read(addr);
        if ((op & 001) != 0) {
            switch (op & 007) {
                case 1: acc = computer.alu.shr(acc, addr);  ar = 0;  break;
                case 5: acc = computer.alu.shl(acc, addr);  ar = 0;  break;
                case 3: acc = computer.alu.mul(acc, n);  ar = 0;  break;
                case 7: acc = ar;  break;  // Note: does NOT clear aux register
            }
        } else {
            switch (op & 007) {
                case 0: acc = computer.alu.longShr(acc, ar, addr);  break;
                case 4: acc = computer.alu.longShl(acc, ar, addr);  break;
                case 2: acc = computer.alu.longMul(acc, n);  break;
                case 6: acc = computer.alu.longDiv(acc, ar, n);  ar = 0;  break;
            }
            ar = computer.alu.getExtension();
        }
        overflow |= computer.alu.isOverflow();
    }

    // Execute a group 6 floating point instruction
    void group6(int op, int addr) {
        // These instructions are the same as an 00 op code, unless the FPU is available.
        if (computer.fpu != null) {
            long n = computer.core.read(addr);
            switch (op & 007) {
                case 0: acc = computer.fpu.add(acc, n);  ar = 0;  break;
                case 1: acc = computer.fpu.sub(acc, n);  ar = 0;  break;
                case 2: acc = computer.fpu.sub(n, acc);  ar = 0;  break;
                case 3: acc = computer.fpu.mul(acc, n);  ar = 0;  break;
                case 4: acc = computer.fpu.div(acc, n);  ar = 0;  break;
                case 5:
                    if (addr < 4096) {
                        acc = computer.fpu.shl(acc, addr % 64);
                    } else {
                        acc = computer.fpu.convert(acc);  ar = 0;
                    }
                    break;
                case 6: acc = computer.fpu.sdiv(acc, n);  break;
                case 7: acc = computer.fpu.sqrt(acc);     break;
            }

            // Set the overflow flags
            overflow |= computer.fpu.isOverflow();
            fpOverflow |= computer.fpu.isFpOverflow();
        }
    }

    // Execute a group 7 control/peripheral instruction
    void group7(int op, int addr) {
        switch (op & 007) {
            // 70 and 73 are standard instructions
            case 0:
                acc = computer.console.read();
                break;
                
            case 3:
                // According to Bill Purvis (on his excellent web pages about the 803 Algol compiler),
                // the 73 instruction puts the address in the upper and lower half of the storage word.
                // I can't think why it would need to do this ... but I believe him, so I'll do the same!
                computer.core.write(addr, Word.asInstr(scr, 0, scr));
                break;

            // 71 and 74 read and write the paper tape readers and punches via the PTS.
            case 1:
                acc |= computer.pts.read(addr);
                break;
            case 4:
                computer.pts.write(addr);
                break;

            // 72 and 75 write and read the 'control' mode devices
            case 2:
                computer.devices.controlWrite(addr, acc);
                break;
            case 5:
                long a = computer.devices.controlRead(addr);
                if (a != Word.NOTHING)
                    acc = a;
                break;

            // 76 and 77 access the 'block' mode devices
            case 6: case 7:
                // TODO: block devices not implemented yet!
                break;
        }
    }

    // Dump
    public void dump(Dump dump) {
        dump.acc = acc;
        dump.ar = ar;
        dump.br = br;
        dump.ir = ir;
        dump.scr = scr;
        dump.scr2 = scr2;
        dump.overflow = overflow;
        dump.fpOverflow = fpOverflow;
    }

    // Trace
    public void trace(Trace trace) {
        this.trace = trace;
        viewTrace();
    }

    // Mainly for debugging
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("CPU:");
        sb.append(" acc=").append(Word.toOctalString(acc));
        sb.append(" scr=").append(scr).append(".").append(scr2);
        sb.append(" ir=\"").append(Word.toInstrString(ir)).append("\"");
        sb.append(" overflow=").append(overflow);
        return sb.toString();
    }

    /*
     * GUI visualisation
     */

    CpuView view;

    public void setView(CpuView view) {
        this.view = view;
    }

    void viewState() {
        if (view != null) {
            view.updateRegisters(acc, ar, br, scr, ir);
            view.updateFlags(overflow, fpOverflow);
        }
    }
    
    void viewTrace() {
        if (view != null) {
            view.updateTrace(trace != null);
        }
    }
}
