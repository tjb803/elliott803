/**
 * Elliott Model 803B Simulator
 *
 * (C) Copyright Tim Baldwin 2009
 */
package elliott803.machine;

import java.util.StringTokenizer;

/**
 * Constants and utility functions to manipulate a single instruction
 * 
 * In general:
 *    getXxxx() methods will extract various values from an instruction
 *    asXxxx() methods will turn various values into an instruction
 *
 * @author Baldwin
 */
public abstract class Instruction {

    public static final int OP_BITS    = 0x3F;
    public static final int ADDR_BITS  = 0x1FFF;

    public static final int INSTR_MASK = 0x7FFFF;           // 19 bits

    /*
     * Set/extract the op code and addr parts of the instruction
     */
    public static final int asOp(int op) {
        return (op & OP_BITS);
    }

    public static final int asAddr(int addr) {
        return (addr & ADDR_BITS);
    }

    public static final int asInstr(int op, int addr) {
        return ((asOp(op)<<13) | asAddr(addr));
    }
    
    public static final int asInstr(int instr) {
        return (instr & INSTR_MASK);
    }

    public static final int getOp(int instr) {
        return ((instr>>13) & OP_BITS);
    }

    public static final int getAddr(int instr) {
        return (instr & ADDR_BITS);
    }

    /*
     * Format an instruction as a String in the for "oo nnnn"
     * where "oo" is the opcode as 2 octal digits
     *       "nnnn" is the address in decimal left-space padded to 4 characters
     */
    public static final String toOpString(int op) {
        StringBuilder result = new StringBuilder();
        String text = Integer.toOctalString(op);
        result.append("00", 0, 2-text.length()).append(text);
        return result.toString();
    }

    public static final String toAddrString(int addr) {
        StringBuilder result = new StringBuilder();
        String text = Integer.toString(addr);
        result.append("    ", 0, 4-text.length()).append(text);
        return result.toString();
    }

    public static final String toInstrString(int instr) {
        StringBuilder result = new StringBuilder();
        String text1 = Integer.toOctalString(getOp(instr));
        String text2 = Integer.toString(getAddr(instr));
        result.append("00", 0, 2-text1.length()).append(text1);
        result.append("     ", 0, 5-text2.length()).append(text2);
        return result.toString();
    }
    
    /*
     * Parse the "oo nnnn" String form to an integer.
     */
    public static final int parseInstr(String s) {
        int op = 0, addr = 0;
        StringTokenizer t = new StringTokenizer(s, " ");
        if (t.hasMoreTokens())
            op = parseOp(t.nextToken());
        if (t.hasMoreTokens())
            addr = parseAddr(t.nextToken());
        return asInstr(op, addr);
    }
    
    public static final int parseOp(String s) {
        return asOp(Integer.parseInt(s, 8));
    }
    
    public static final int parseAddr(String s) {
        return asAddr(Integer.parseInt(s));
    }
}
