/**
 * Elliott Model 803B Simulator
 *
 * (C) Copyright Tim Baldwin 2009,2013
 */
package elliott803.utils;

import java.io.File;
import java.io.PrintStream;
import java.text.DateFormat;

import elliott803.machine.Dump;
import elliott803.machine.Instruction;
import elliott803.machine.Word;
import elliott803.telecode.Telecode;

/**
 * This class will print a formatted core dump.
 *
 * Usage:
 *    PrintCore corefile [outputfile]
 *
 * where:
 *    corefile: is the name of a core dump file
 *    outputfile: is the name of the formatted output file, default is standard out.
 *
 * @author Baldwin
 */
public class PrintCore {
    
    private static final int MAX_INT = 1<<19;

    public static void main(String[] args) throws Exception {
        // Handle parameters
        Args parms = new Args("PrintCore", "corefile [outputfile]", args, null);
        File inputFile = parms.getInputFile(1);
        File outputFile = parms.getOutputFile(2);

        // Check parameters
        if (inputFile == null) {
            parms.usage();
        }

        // Create input and output streams
        PrintStream output = System.out;
        if (outputFile != null) {
            output = new PrintStream(outputFile);
        }

        // Read the core dump
        Dump dump = Dump.readDump(inputFile);
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);

        if (dump != null) {
            output.println("Elliott 803B Core Dump");
            output.println("created: " + df.format(dump.timestamp));
            output.println();

            // Format the output
            PrintCore formatter = new PrintCore(output);
            output.println("CPU registers:");
            formatter.printCpu(dump);
            output.println("Core store:");
            formatter.printStore(dump.core, 0, 8192);
        } else {
            System.out.println("No core dump found");
        }
    }

    PrintStream output;

    PrintCore(PrintStream out) {
        output = out;
    }

    // Format CPU status
    void printCpu(Dump dump) {
        output.println("  ACC: " + Word.toBinaryString(dump.acc));
        output.println("   AR: " + Word.toBin38String(dump.ar));
        output.println("  SCR: " + Instruction.toAddrString(dump.scr) + "." + dump.scr2);
        output.println("   IR: " + Word.toInstrString(dump.ir));
        output.println();
        output.println("  Overflow:   " + dump.overflow);
        output.println("  FP Overflow: " + dump.fpOverflow);
        output.println();
    }

    // Format storage.  Multiple lines of zeros are compressed.
   void printStore(long[] store, int addr, int length) {
        long lastWord = 0;
        int duplicates = 0;
        for (int i = addr; i < addr+length; i++) {
            long word = store[i];
            if (i == addr+length-1 || word != lastWord) {
                if (duplicates > 0) {
                    if (duplicates < 5 || (lastWord != 0 && duplicates < 8)) {
                        while (duplicates > 0) {
                            printLine(i-duplicates, lastWord);
                            duplicates -= 1;
                        }
                    } else {
                        output.println("         ...");
                    }
                    duplicates = 0;
                }
                printLine(i, word);
                lastWord = word;
            } else {
                duplicates += 1;
            }
        }
        output.println();
    }

    // Print a storage line
    private void printLine(int addr, long word) {
        if (word == 0) {
            output.println(Instruction.toAddrString(addr) + ":    0");
        } else {
            String text = Instruction.toAddrString(addr) + ":   ";
            if (addr < 4) {
                text += "(" + Word.toInstrString(word) + ")";
            } else {
                text += " " + Word.toInstrString(word) + "   ";
                text += "[" + Word.toOctalString(word) + "]   ";
                if (Word.getLong(word) > -MAX_INT && Word.getLong(word) < MAX_INT) {
                    text += "(" + Word.toIntegerString(word) + ")";
                } else { 
                    text += "(" + Word.toFloatString(word) + ")";
                }    
                if (word > 1 && word < 32) {
                    int ch = (int)word;
                    if (ch < 10) text += " ";
                    switch (ch) {
                        case 27: text += "    FS"; break;
                        case 28: text += "   SPC"; break;
                        case 29: text += "   CR"; break;
                        case 30: text += "   LF"; break;
                        case 31: text += "   LS"; break;
                        default: text += "  '" + Telecode.asLetter(ch) + " " + Telecode.asFigure(ch) + "'";
                    }
                } else if (word > 63) {
                    char[] pt = new char[6];
                    for (int i = 5; i >= 0; i--) {
                        int ch = (int)word & Telecode.CHAR_MASK;
                        int sh = (int)word & (Telecode.CHAR_MASK+1);
                        if (ch > 0 && (ch < Telecode.TELE_FS || ch == Telecode.TELE_SP)) {
                            pt[i] = (sh != 0) ? Telecode.asLetter(ch) : Telecode.asFigure(ch);
                        } else {
                            pt[i] = '.';
                        }    
                        word >>= 6;
                    }
                    for (int i = text.length(); i < 67; i++) text += " ";
                    text += "\"" + new String(pt) + "\"";
                }
            }
            output.println(text);
        }
    }
}
