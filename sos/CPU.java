package sos;

import java.util.*;

/**
 * This class is the centerpiece of a simulation of the essential hardware of a
 * microcomputer.  This includes a processor chip, RAM and I/O devices.  It is
 * designed to demonstrate a simulated operating system (SOS).
 *
 * File History:
 * HW 1 Stephen Robinson and Camden McKone,
 * HW 2 Stephen Robinson and Nathan Brown
 * HW 3 Stephen Robinson and Connor Haas
 * HW 4 --Unchanged--
 * HW 5 Stephen Robinson and Jason Vanderwerf
 *
 * @see RAM
 * @see SOS
 * @see Program
 * @see Sim
 */

public class CPU implements Runnable
{
    
    //======================================================================
    //Constants
    //----------------------------------------------------------------------

    //These constants define the instructions available on the chip
    public static final int SET    = 0;    /* set value of reg */
    public static final int ADD    = 1;    // put reg1 + reg2 into reg3
    public static final int SUB    = 2;    // put reg1 - reg2 into reg3
    public static final int MUL    = 3;    // put reg1 * reg2 into reg3
    public static final int DIV    = 4;    // put reg1 / reg2 into reg3
    public static final int COPY   = 5;    // copy reg1 to reg2
    public static final int BRANCH = 6;    // goto address in reg
    public static final int BNE    = 7;    // branch if not equal
    public static final int BLT    = 8;    // branch if less than
    public static final int POP    = 9;    // load value from stack
    public static final int PUSH   = 10;   // save value to stack
    public static final int LOAD   = 11;   // load value from heap
    public static final int SAVE   = 12;   // save value to heap
    public static final int TRAP   = 15;   // system call
    
    //These constants define the indexes to each register
    public static final int R0   = 0;     // general purpose registers
    public static final int R1   = 1;
    public static final int R2   = 2;
    public static final int R3   = 3;
    public static final int R4   = 4;
    public static final int PC   = 5;     // program counter
    public static final int SP   = 6;     // stack pointer
    public static final int BASE = 7;     // bottom of currently accessible RAM
    public static final int LIM  = 8;     // top of accessible RAM
    public static final int NUMREG = 9;   // number of registers

    //Misc constants
    public static final int NUMGENREG = PC; // the number of general registers
    public static final int INSTRSIZE = 4;  // number of ints in a single instr +
                                            // args.  (Set to a fixed value for simplicity.)

    //======================================================================
    //Member variables
    //----------------------------------------------------------------------
    /**
     * specifies whether the CPU should output details of its work
     **/
    private boolean m_verbose = true;

    /**
     * This array contains all the registers on the "chip".
     **/
    private int m_registers[];

    /**
     * A pointer to the RAM used by this CPU
     *
     * @see RAM
     **/
    private RAM m_RAM = null;


    /**
     * A pointer to the CPU's interrupt controller.
     *
     * @see InterruptController
     **/
    private InterruptController m_IC = null;

    //======================================================================
    //Callback Interface
    //----------------------------------------------------------------------

    /**
     * TrapHandler
     *
     * This interface should be implemented by the operating system to allow the
     * simulated CPU to generate hardware interrupts and system calls.
     */
    public interface TrapHandler
    {
        public void interruptIOReadComplete(int devID, int addr, int data);
        public void interruptIOWriteComplete(int devID, int addr);
        public void interruptIllegalMemoryAccess(int addr);
        public void interruptDivideByZero();
        public void interruptIllegalInstruction(int[] instr);
        public void systemCall();
    };//interface TrapHandler

    
    /**
     * a reference to the trap handler for this CPU.  On a real CPU this would
     * simply be an address that the PC register is set to.
     */
    private TrapHandler m_TH = null;


    //======================================================================
    //Methods
    //----------------------------------------------------------------------

    /**
     * CPU ctor
     *
     * Intializes all member variables.
     */
    public CPU(RAM ram, InterruptController ic)
    {
        m_registers = new int[NUMREG];
        for(int i = 0; i < NUMREG; i++)
        {
            m_registers[i] = 0;
        }
        m_RAM = ram;

        m_IC = ic;

    }//CPU ctor 

    /**
     * registerTrapHandler
     *
     * allows SOS to register itself as the trap handler 
     */
    public void registerTrapHandler(TrapHandler th)
    {
        m_TH = th;
    }

    /**
     * getPC
     *
     * @return the value of the program counter
     */
    public int getPC()
    {
        return m_registers[PC];
    }

    /**
     * getSP
     *
     * @return the value of the stack pointer
     */
    public int getSP()
    {
        return m_registers[SP];
    }

    /**
     * getBASE
     *
     * @return the value of the base register
     */
    public int getBASE()
    {
        return m_registers[BASE];
    }

    /**
     * getLIM
     *
     * @return the value of the limit register
     */
    public int getLIM()
    {
        return m_registers[LIM];
    }

    /**
     * getRegisters
     *
     * @return the registers
     */
    public int[] getRegisters()
    {
        return m_registers;
    }

    /**
     * setPC
     *
     * @param v the new value of the program counter
     */
    public void setPC(int v)
    {
        m_registers[PC] = v;
    }

    /**
     * setSP
     *
     * @param v the new value of the stack pointer
     */
    public void setSP(int v)
    {
        m_registers[SP] = v;
    }

    /**
     * setBASE
     *
     * @param v the new value of the base register
     */
    public void setBASE(int v)
    {
        m_registers[BASE] = v;
    }

    /**
     * setLIM
     *
     * @param v the new value of the limit register
     */
    public void setLIM(int v)
    {
        m_registers[LIM] = v;
    }

    /**
     * regDump
     *
     * Prints the values of the registers.  Useful for debugging.
     */
    public void regDump()
    {
        for(int i = 0; i < NUMGENREG; i++)
        {
            System.out.print("r" + i + "=" + m_registers[i] + " ");
        }//for
        System.out.print("PC=" + m_registers[PC] + " ");
        System.out.print("SP=" + m_registers[SP] + " ");
        System.out.print("BASE=" + m_registers[BASE] + " ");
        System.out.print("LIM=" + m_registers[LIM] + " ");
        System.out.println("");
    }//regDump

    /**
     * printIntr
     *
     * Prints a given instruction in a user readable format.  Useful for
     * debugging.
     *
     * @param instr the current instruction
     */
    public static void printInstr(int[] instr)
    {
            switch(instr[0])
            {
                case SET:
                    System.out.println("SET R" + instr[1] + " = " + instr[2]);
                    break;
                case ADD:
                    System.out.println("ADD R" + instr[1] + " = R" + instr[2] + " + R" + instr[3]);
                    break;
                case SUB:
                    System.out.println("SUB R" + instr[1] + " = R" + instr[2] + " - R" + instr[3]);
                    break;
                case MUL:
                    System.out.println("MUL R" + instr[1] + " = R" + instr[2] + " * R" + instr[3]);
                    break;
                case DIV:
                    System.out.println("DIV R" + instr[1] + " = R" + instr[2] + " / R" + instr[3]);
                    break;
                case COPY:
                    System.out.println("COPY R" + instr[1] + " = R" + instr[2]);
                    break;
                case BRANCH:
                    System.out.println("BRANCH @" + instr[1]);
                    break;
                case BNE:
                    System.out.println("BNE (R" + instr[1] + " != R" + instr[2] + ") @" + instr[3]);
                    break;
                case BLT:
                    System.out.println("BLT (R" + instr[1] + " < R" + instr[2] + ") @" + instr[3]);
                    break;
                case POP:
                    System.out.println("POP R" + instr[1]);
                    break;
                case PUSH:
                    System.out.println("PUSH R" + instr[1]);
                    break;
                case LOAD:
                    System.out.println("LOAD R" + instr[1] + " <-- @R" + instr[2]);
                    break;
                case SAVE:
                    System.out.println("SAVE R" + instr[1] + " --> @R" + instr[2]);
                    break;
                case TRAP:
                    System.out.println("TRAP");
                    break;
                default:        // should never be reached
                    System.out.println("?? ");
                    break;          
            }//switch

    }//printInstr


    /**
     * checkForIOInterrupt
     *
     * Checks the databus for signals from the interrupt controller and, if
     * found, invokes the appropriate handler in the operating system.
     *
     */
    private void checkForIOInterrupt()
    {
        //If there is no interrupt to process, do nothing
        if (m_IC.isEmpty())
        {
            return;
        }
        
        //Retreive the interrupt data
        int[] intData = m_IC.getData();

        //Report the data if in verbose mode
        if (m_verbose)
        {
            System.out.println("CPU received interrupt: type=" + intData[0]
                               + " dev=" + intData[1] + " addr=" + intData[2]
                               + " data=" + intData[3]);
        }

        //Dispatch the interrupt to the OS
        switch(intData[0])
        {
            case InterruptController.INT_READ_DONE:
                m_TH.interruptIOReadComplete(intData[1], intData[2], intData[3]);
                break;
            case InterruptController.INT_WRITE_DONE:
                m_TH.interruptIOWriteComplete(intData[1], intData[2]);
                break;
            default:
                System.out.println("CPU ERROR:  Illegal Interrupt Received.");
                System.exit(-1);
                break;
        }//switch

    }//checkForIOInterrupt


    /**
     * validMemory
     *
     * Determines if physical address respects BASE and LIM registers.
     *
     * @param addr the address to check
     * @param registers the registers array to use.
     *
     * @return true iff the address is valid.
     */
    public boolean validMemory(int addr, int[] registers){
        return (addr >= registers[BASE] && addr <= registers[LIM]);
    }

    /**
     * validMemory
     *
     * Determines if physical address respects BASE and LIM registers.
     *
     * @param addr the address to check
     *
     * @return true iff the address is valid.
     */
    public boolean validMemory(int addr){
        return validMemory(addr, m_registers);
    }

    /**
     * pushStack
     *
     * Pushes a value to the stack.
     *
     * @param value the value to push to the stack.
     * @param registers the registers array to use.
     */
    public void pushStack(int value, int[] registers) {
        if (!validMemory(m_registers[SP] + registers[BASE], registers)) {
            //Stack overflow!
            //This was probably deliberate because we had to overwrite the
            //program with stack memory to do this.
            m_TH.interruptIllegalMemoryAccess(registers[SP] + registers[BASE]);
        }
        m_RAM.write(registers[SP] + registers[BASE], value);
        registers[SP]--;
    }

    /**
     * popStack
     *
     * Pops a value from the stack.
     *
     * @return The value poped from the stack.
     * @param registers the registers array to use.
     */
    public int popStack(int[] registers) {
        registers[SP]++;
        if (!validMemory(registers[SP] + registers[BASE], registers)) {
            //Stack underflow!
            m_TH.interruptIllegalMemoryAccess(registers[SP] + registers[BASE]);
        }
        return m_RAM.read(registers[SP] + registers[BASE]);
    }

    /**
     * pushStack
     *
     * Pushes a value to the stack.
     *
     * @param value the value to push to the stack.
     */
    public void pushStack(int value) {
        pushStack(value, m_registers);
    }

    /**
     * popStack
     *
     * Pops a value from the stack.
     *
     * @return The value poped from the stack.
     */
    public int popStack() {
        return popStack(m_registers);
    }

    /**
     * run
     *
     * Start the CPU simulation. Exits only on chrash or exit trap.
     */
    public void run()
    {
       
        while (true) {

            //Check for ID Interrupt
            checkForIOInterrupt();

            //Fetch next instruction
            int instr[] = m_RAM.fetch(m_registers[BASE] + m_registers[PC]);

            //Debug information if enabled
            if (m_verbose) {
                System.out.println(".");
                regDump();
                printInstr(instr);
            }

            //Determine action to take for instruction
            int addr;
            switch(instr[0]) {
                case SET:
                    m_registers[instr[1]] = instr[2];
                    break;
                case ADD:
                    m_registers[instr[1]] = m_registers[instr[2]] +
                                            m_registers[instr[3]];
                    break;
                case SUB:
                    m_registers[instr[1]] = m_registers[instr[2]] -
                                            m_registers[instr[3]];
                    break;
                case MUL:
                    m_registers[instr[1]] = m_registers[instr[2]] *
                                            m_registers[instr[3]];
                    break;
                case DIV:
                    if (m_registers[instr[3]] == 0) {
                        m_TH.interruptDivideByZero();
                        return;
                    }
                    m_registers[instr[1]] = m_registers[instr[2]] /
                                            m_registers[instr[3]];
                    break;
                case COPY:
                    m_registers[instr[1]] = m_registers[instr[2]];
                    break;
                case BRANCH:
                    m_registers[PC] = instr[1] - 4;
                    break;
                case BNE:
                    if (m_registers[instr[1]] != m_registers[instr[2]]) {
                        m_registers[PC] = instr[3] - 4;
                    }
                    break;
                case BLT:
                    if (m_registers[instr[1]] < m_registers[instr[2]]) {
                        m_registers[PC] = instr[3] - 4;
                    }
                    break;
                case POP:
                    m_registers[instr[1]] = popStack();
                    break;
                case PUSH:
                    pushStack(m_registers[instr[1]]);
                    break;
                case LOAD:
                    addr = m_registers[instr[2]] + m_registers[BASE];
                    if (!validMemory(addr)) {
                        m_TH.interruptIllegalMemoryAccess(addr);
                        return;
                    }
                    m_registers[instr[1]] = m_RAM.read(addr);
                    break;
                case SAVE:
                    addr = m_registers[instr[2]] + m_registers[BASE];
                    if (!validMemory(addr)) {
                        m_TH.interruptIllegalMemoryAccess(addr);
                        return;
                    }
                    m_RAM.write(addr, m_registers[instr[1]]);
                    break;
                case TRAP:
                    m_TH.systemCall();
                    break;
                default: // This is bad. Why did this happen to me?
                    m_TH.interruptIllegalInstruction(instr);
                    return;
            }//switch

            m_registers[PC] += INSTRSIZE; //Increment the PC counter

            //Check for out of bounds PC
            if (!validMemory(m_registers[BASE] + m_registers[PC])) {
                m_TH.interruptIllegalMemoryAccess(m_registers[BASE] + m_registers[PC]);
                return;
            }
        }

    }//run
    
};//class CPU

// vim: foldmethod=marker foldmarker={,} foldlevel=0
