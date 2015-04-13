package sos;

import java.util.*;

/**
 * This class contains the simulated operating system (SOS).  Realistically it
 * would run on the same processor (CPU) that it is managing but instead it uses
 * the real-world processor in order to allow a focus on the essentials of
 * operating system design using a high level programming language.
 *
 * File History:
 * HW 1 Stephen Robinson and Camden McKone,
 * HW 2 Stephen Robinson and Nathan Brown
 * HW 3 Stephen Robinson and Connor Haas
 * HW 4 Stephen Robinson and Jordan White
 * HW 5 Stephen Robinson and Jason Vanderwerf
 * HW 6 Stephen Robinson and Davis Achong
 * HW 7 Stephen Robinson and Nathan Travanti
 */

public class SOS implements CPU.TrapHandler
{
    //======================================================================
    //Member variables
    //----------------------------------------------------------------------

    /**
     * This flag causes the SOS to print lots of potentially helpful
     * status messages
     **/
    public static final boolean m_verbose = true;

    /**
     * The ProcessControlBlock of the current process
     **/
    private ProcessControlBlock m_currProcess = null;

    /**
     * List of all processes currently loaded into RAM and in one of the major states
     **/
    Vector<ProcessControlBlock> m_processes = null;

    /**
     * List of all free blocks.
     */
    private Vector<MemBlock> m_freeList = null;

    /**
     * A Vector of DeviceInfo objects
     **/
    private Vector<DeviceInfo> m_devices = null;

    /**
     * A Vector of all the Program objects that are available to the operating system.
     **/
    Vector<Program> m_programs = null;

    /**
     * The ID which will be assigned to the next process that is loaded
     **/
    private int m_nextProcessID = 1001;

    /**
     * The CPU the operating system is managing.
     **/
    private CPU m_CPU = null;

    /**
     * The RAM attached to the CPU.
     **/
    private RAM m_RAM = null;

    /**
     * The MMU in the CPU.
     **/
    private MMU m_MMU = null;

    //======================================================================
    //Constants
    //----------------------------------------------------------------------

    //These constants define the system calls this OS can currently handle
    public static final int SYSCALL_EXIT     = 0;    /* exit the current program */
    public static final int SYSCALL_OUTPUT   = 1;    /* outputs a number */
    public static final int SYSCALL_GETPID   = 2;    /* get current process id */
    public static final int SYSCALL_OPEN    = 3;    /* access a device */
    public static final int SYSCALL_CLOSE   = 4;    /* release a device */
    public static final int SYSCALL_READ    = 5;    /* get input from device */
    public static final int SYSCALL_WRITE   = 6;    /* send output to device */
    public static final int SYSCALL_EXEC    = 7;    /* spawn a new process */
    public static final int SYSCALL_YIELD   = 8;    /* yield the CPU to another process */
    public static final int SYSCALL_COREDUMP = 9;    /* print process state and exit */

    //Return codes for syscalls
    public static final int SYSCALL_RET_SUCCESS = 0;    /* no problem */
    public static final int SYSCALL_RET_DNE = 1;    /* device doesn't exist */
    public static final int SYSCALL_RET_NOT_SHARE = 2;    /* device is not sharable */
    public static final int SYSCALL_RET_ALREADY_OPEN = 3;    /* device is already open */
    public static final int SYSCALL_RET_NOT_OPEN = 4;    /* device is not yet open */
    public static final int SYSCALL_RET_RO = 5;    /* device is read only */
    public static final int SYSCALL_RET_WO = 6;    /* device is write only */

    /**This process is used as the idle process' id*/
    public static final int IDLE_PROC_ID    = 999;  

    /*======================================================================
     * Constructors & Debugging
     *----------------------------------------------------------------------
     */

    /**
     * The constructor does nothing special
     */
    public SOS(CPU c, RAM r, MMU mmu)
    {
        //Init member list
        m_CPU = c;
        m_CPU.registerTrapHandler(this);
        m_RAM = r;
        m_MMU = mmu;

        m_devices = new Vector<DeviceInfo>();
        m_programs = new Vector<Program>();
        m_processes = new Vector<ProcessControlBlock>();
        m_freeList = new Vector<MemBlock>();

        initPageTable();

        //One page is not really free ram because it is not 
        int freeRam = (m_MMU.getNumPages() - 1) * m_MMU.getPageSize;

        //Initially all ram is one free block.
        m_freeList.add(new MemBlock(0, freeRam);

    }//SOS ctor

    /**
     * Does a System.out.print as long as m_verbose is true
     **/
    public static void debugPrint(String s)
    {
        if (m_verbose)
        {
            System.out.print(s);
        }
    }

    /**
     * Does a System.out.println as long as m_verbose is true
     **/
    public static void debugPrintln(String s)
    {
        if (m_verbose)
        {
            System.out.println(s);
        }
    }


    /*======================================================================
     * Memory Block Management Methods
     *----------------------------------------------------------------------
     */

    /**
     * Find the next free block in memory large enough for a program of size.
     * If there is not enough contiguous free memory then defrag the ram.
     *
     * The size is rounded up to the nearest pageSize.
     *
     * @param size The size the block of free memory needs to be.
     * @return the address to place the program in ram or -1 if there is not
     * enough room.
     */
    private int allocBlock(int size)
    {

        //Round up the size to the nearest pageSize
        int rem = size % m_MMU.getPageSize()
        if (rem != 0)
        {
            size += m_MMU.getPageSize() - rem;
        }

        //Check if all memory has been allocated
        if (m_freeList.size() == 0) {
            return -1; //you are screwed...
        }

        //Find the free memory block where this process is going to be added to.
        MemBlock block = null;
        for( MemBlock searchBlock : m_freeList ) {
            if (searchBlock.getSize() >= size) {
                block = searchBlock;
                break;
            }
        }

        //Defrag memory if no block is large enough.
        if (block == null) {
            //We need to bubble all the processes up to the top of ram
            Collections.sort(m_processes);
            Collections.sort(m_freeList);


            for (ProcessControlBlock pcb : m_processes) {

                MemBlock curFreeBlock = m_freeList.get(0);

                if (pcb.registers[CPU.BASE] == curFreeBlock.getAddr() + curFreeBlock.getSize()) {

                    //Move this proc up in ram.
                    pcb.move(curFreeBlock.getAddr());

                    //Bubble up the free block
                    curFreeBlock.m_addr = pcb.registers[CPU.LIM];

                    //Check if this free block can be merged with the next one.
                    if (m_freeList.size() > 1)
                    {
                        MemBlock nextFreeBlock = m_freeList.get(1);
                        if (curFreeBlock.getAddr() + curFreeBlock.getSize() ==
                                nextFreeBlock.getAddr())
                        {
                            m_freeList.remove(nextFreeBlock);
                            curFreeBlock.m_size += nextFreeBlock.getSize();
                        }
                    }
                }
            }

            block = m_freeList.get(0);
        }


        //Check that there is room in the one free block at the end of ram.
        if (block.getSize() < size) {
            return -1; //Well shit.
        }

        //Shrink the size of the block because it is now allocated.
        int allocAddr = block.getAddr();
        block.m_addr += size;
        block.m_size -= size;

        if (block.m_size == 0) {
            m_freeList.remove(block);
        }

        return allocAddr;

    }//allocBlock

    /**
     * Free the memory block used by the current process.
     * Merges the free memory if needed.
     */
    private void freeCurrProcessMemBlock()
    {
        if (m_currProcess == null) {
            return;
        }

        MemBlock newBlock = new MemBlock(
                m_currProcess.registers[CPU.BASE],
                m_currProcess.registers[CPU.LIM] - m_currProcess.registers[CPU.BASE]
                );

        m_freeList.add(newBlock);
        Collections.sort(m_freeList);

        //Find the blocks immediatly above and below the newBlock
        int i = m_freeList.indexOf(newBlock);
        MemBlock aboveBlock = null;
        MemBlock belowBlock = null;
        if (i-1 > 0) {
            aboveBlock = m_freeList.get(i-1);
        }
        if (i+1 < m_freeList.size()) {
            belowBlock = m_freeList.get(i+1);
        }

        //Check if we can merge with the above block
        if (aboveBlock != null && 
                aboveBlock.getAddr() + aboveBlock.getSize() == newBlock.getAddr())
        {
            newBlock.m_addr -= aboveBlock.getSize();
            newBlock.m_size += aboveBlock.getSize();
            m_freeList.remove(aboveBlock);
        }

        //Check if we can merge with the below block
        if (belowBlock != null && 
                newBlock.getAddr() + newBlock.getSize() == belowBlock.getAddr())
        {
            newBlock.m_size += belowBlock.getSize();
            m_freeList.remove(belowBlock);
        }

    }//freeCurrProcessMemBlock

    /**
     * printMemAlloc                 *DEBUGGING*
     *
     * outputs the contents of m_freeList and m_processes to the console and
     * performs a fragmentation analysis.  It also prints the value in
     * RAM at the BASE and LIMIT registers.  This is useful for
     * tracking down errors related to moving process in RAM.
     *
     * SIDE EFFECT:  The contents of m_freeList and m_processes are sorted.
     *
     */
    private void printMemAlloc()
    {
        //If verbose mode is off, do nothing
        if (!m_verbose) return;

        //Print a header
        System.out.println("\n----------========== Memory Allocation Table ==========----------");
        
        //Sort the lists by address
        Collections.sort(m_processes);
        Collections.sort(m_freeList);

        //Initialize references to the first entry in each list
        MemBlock m = null;
        ProcessControlBlock pi = null;
        ListIterator<MemBlock> iterFree = m_freeList.listIterator();
        ListIterator<ProcessControlBlock> iterProc = m_processes.listIterator();
        if (iterFree.hasNext()) m = iterFree.next();
        if (iterProc.hasNext()) pi = iterProc.next();

        //Loop over both lists in order of their address until we run out of
        //entries in both lists
        while ((pi != null) || (m != null))
        {
            //Figure out the address of pi and m.  If either is null, then assign
            //them an address equivalent to +infinity
            int pAddr = Integer.MAX_VALUE;
            int mAddr = Integer.MAX_VALUE;
            if (pi != null)  pAddr = pi.getRegisterValue(CPU.BASE);
            if (m != null)  mAddr = m.getAddr();

            //If the process has the lowest address then print it and get the
            //next process
            if ( mAddr > pAddr )
            {
                int size = pi.getRegisterValue(CPU.LIM) - pi.getRegisterValue(CPU.BASE);
                System.out.print(" Process " + pi.processId +  " (addr=" + pAddr + " size=" + size + " words");
                System.out.print(" / " + (size / m_MMU.getPageSize()) + " pages)" );
                System.out.print(" @BASE=" + m_MMU.read(pi.getRegisterValue(CPU.BASE))
                                 + " @SP=" + m_MMU.read(pi.getRegisterValue(CPU.SP)));
                System.out.println();
                if (iterProc.hasNext())
                {
                    pi = iterProc.next();
                }
                else
                {
                    pi = null;
                }
            }//if
            else
            {
                //The free memory block has the lowest address so print it and
                //get the next free memory block
                System.out.println("    Open(addr=" + mAddr + " size=" + m.getSize() + ")");
                if (iterFree.hasNext())
                {
                    m = iterFree.next();
                }
                else
                {
                    m = null;
                }
            }//else
        }//while
            
        //Print a footer
        System.out.println("-----------------------------------------------------------------");
        
    }//printMemAlloc
    



    /*======================================================================
     * Virtual Memory Methods
     *----------------------------------------------------------------------
     */

    /**
     * Initialize the page table in RAM. Initially all virtual addresses
     * will correspond to physical ones.
     */
    private void initPageTable()
    {
        //Note: one page is resurved for the page table.
        for (int i = 0; i < m_MMU.getNumPages() - 1; ++i) 
        {
            if (i < m_MMU.getNumFrames() - 1) 
            {
                //Because the first page is reserved for the page table, 
                //all addresses must be offset by one to skip it.
                m_RAM.write(i, i+1);
            }
            else 
            {
                m_RAM.write(i, -1);
            }
        }
    }//initPageTable


    /**
     * createPageTableEntry
     *
     * is a helper method for {@link #printPageTable} to create a single entry
     * in the page table to print to the console.  This entry is formatted to be
     * exactly 35 characters wide by appending spaces.
     *
     * @param pageNum is the page to print an entry for
     *
     */
    private String createPageTableEntry(int pageNum)
    {
        int frameNum = m_MMU.read(pageNum);
        int baseAddr = frameNum * m_MMU.getPageSize();

        //check to see if student has pre-shifted frame numbers
        //in their page table and, if so, correct the values
        if (frameNum / m_MMU.getPageSize() != 0)
        {
            baseAddr = frameNum;
            frameNum /= m_MMU.getPageSize();
        }

        String entry = "page " + pageNum + "-->frame "
            + frameNum + " (@" + baseAddr +")";

        //pad out to 35 characters
        String format = "%s%" + (35 - entry.length()) + "s";
        return String.format(format, entry, " ");

    }//createPageTableEntry

    /**
     * printPageTable      *DEBUGGING*
     *
     * prints the page table in a human readable format
     *
     */
    private void printPageTable()
    {
        //If verbose mode is off, do nothing
        if (!m_verbose) return;

        //Print a header
        System.out.println("\n----------========== Page Table ==========----------");

        //Print the entries in two columns
        for(int i = 0; i < m_MMU.getNumPages() / 2; i++)
        {
            String line = createPageTableEntry(i);                       //left column
            line += createPageTableEntry(i + (m_MMU.getNumPages() / 2)); //right column
            System.out.println(line);
        }

        //Print a footer
        System.out.println("-----------------------------------------------------------------");

    }//printPageTable


    /*======================================================================
     * Device Management Methods
     *----------------------------------------------------------------------
     */

    /**
     * registerDevice
     *
     * adds a new device to the list of devices managed by the OS
     *
     * @param dev     the device driver
     * @param id      the id to assign to this device
     * 
     */
    public void registerDevice(Device dev, int id)
    {
        m_devices.add(new DeviceInfo(dev, id));
    } //registerDevice

    /**
     * getDeviceInfo
     *
     * gets a device info by id.
     *
     * @param id      the id of the device
     * @return        the device info instance if it exists, else null
     * 
     */
    private DeviceInfo getDeviceInfo(int id) {
        Iterator<DeviceInfo> i = m_devices.iterator();

        while(i.hasNext()) {
            DeviceInfo devInfo = i.next();
            if (devInfo.getId() == id) {
                return devInfo;
            }
        }

        return null;
    }

    /*======================================================================
     * Process Management Methods
     *----------------------------------------------------------------------
     */

    /**
     * createIdleProcess
     *
     * creates a one instruction process that immediately exits.  This is used
     * to buy time until device I/O completes and unblocks a legitimate
     * process.
     *
     */
    public void createIdleProcess()
    {
        int progArr[] = { 0, 0, 0, 0,   //SET r0=0
            0, 0, 0, 0,   //SET r0=0
            //(repeated instruction to account for vagaries in student
            //implementation of the CPU class)
            10, 0, 0, 0,   //PUSH r0
            15, 0, 0, 0 }; //TRAP

        //Initialize the starting position for this program
        int baseAddr = allocBlock(progArr.length);
        if (baseAddr == -1) {
            System.out.println("FATAL: Out of memory for idle process!");
            System.exit(0);
        }

        //Load the program into RAM
        for(int i = 0; i < progArr.length; i++)
        {
            m_MMU.write(baseAddr + i, progArr[i]);
        }

        //Save the register info from the current process (if there is one)
        if (m_currProcess != null)
        {
            m_currProcess.save(m_CPU);
        }

        //Set the appropriate registers
        m_CPU.setPC(0);
        m_CPU.setSP(progArr.length + 20);
        m_CPU.setBASE(baseAddr);
        m_CPU.setLIM(baseAddr + progArr.length + 20);

        //Save the relevant info as a new entry in m_processes
        m_currProcess = new ProcessControlBlock(IDLE_PROC_ID);  
        m_processes.add(m_currProcess);

    }//createIdleProcess


    /**
     * printProcessTable      **DEBUGGING**
     *
     * prints all the processes in the process table
     */
    private void printProcessTable()
    {
        debugPrintln("");
        debugPrintln("Process Table (" + m_processes.size() + " processes)");
        debugPrintln("======================================================================");
        for(ProcessControlBlock pi : m_processes)
        {
            debugPrintln("    " + pi);
        }//for
        debugPrintln("----------------------------------------------------------------------");

    }//printProcessTable

    /**
     * removeCurrentProcess
     *
     * Removes the currently running process from the list of all processes.
     * Schedules a new process.
     */
    public void removeCurrentProcess()
    {
        if (m_currProcess != null) {

            //Free the memory from exiting process
            freeCurrProcessMemBlock();

            m_processes.remove(m_currProcess);
            m_currProcess = null;
        }
        scheduleNewProcess();
    }//removeCurrentProcess

    /**
     * selectBlockedProcess
     *
     * select a process to unblock that might be waiting to perform a given
     * action on a given device.  This is a helper method for system calls
     * and interrupts that deal with devices.
     *
     * @param dev   the Device that the process must be waiting for
     * @param op    the operation that the process wants to perform on the
     *              device.  Use the SYSCALL constants for this value.
     * @param addr  the address the process is reading from.  If the
     *              operation is a Write or Open then this value can be
     *              anything
     *
     * @return the process to unblock -OR- null if none match the given criteria
     */
    public ProcessControlBlock selectBlockedProcess(Device dev, int op, int addr)
    {
        DeviceInfo devInfo = getDeviceInfo(dev.getId());
        ProcessControlBlock selected = null;

        for(ProcessControlBlock pi : devInfo.getPCBs())
        {
            if (pi.isBlockedForDevice(dev, op, addr))
            {
                selected = pi;
                break;
            }
        }//for

        return selected;

    }//selectBlockedProcess


    /**
     * chooseProcess
     *
     * selects a non-Blocked process according to the scheduling algorithm.
     *
     * @return a reference to the ProcessControlBlock struct of the selected process
     * -OR- null if no non-blocked process exists
     */
    ProcessControlBlock chooseProcess()
    {
        if (m_currProcess != null) {

            //If we can, we will want to keep the current process running
            if (! m_currProcess.isBlocked()) {

                ++m_currProcess.quantum_used;

                //Check if the process is being selfish and needs a time-out.
                if (m_currProcess.quantum_used > m_currProcess.quantum) {

                    //This proc is beeing selfish. Cut back on its quantum.
                    --m_currProcess.quantum;
                    if (m_currProcess.quantum < m_currProcess.MIN_QUANTUM) {
                        m_currProcess.quantum = m_currProcess.MIN_QUANTUM;
                    }

                } else {
                    return m_currProcess;
                }
            }

            //Reset its quatum_used to give it another chance later.
            m_currProcess.quantum_used = 0;
        }

        ProcessControlBlock newProc = null;

        //We will see if there is a better process to run, but if there isn't
        //then we will just keep going with the current process if it isn't
        //blocked.
        if (! (m_currProcess == null) && ! m_currProcess.isBlocked()) {
            newProc = m_currProcess;
        }

        //The highest priority found so far.
        double maxPriority = -1.;

        //Find the most starved process
        for (ProcessControlBlock pcb: m_processes) {

            if (pcb != m_currProcess && ! pcb.isBlocked()) {

                //We need to compute the starve time ourselves here. The
                //value in the ProcessControlBlock is not updated constantly.
                int starveTime = m_CPU.getTicks() - (int)pcb.getLastReadyTime();

                //Feed the starving procs
                if (starveTime > pcb.STARVED) {
                    pcb.quantum = pcb.MAX_QUANTUM;
                }

                //The quantum tells us how important this proc is:
                double priority = starveTime * pcb.quantum;

                if (priority > maxPriority) {
                    maxPriority = priority;
                    newProc = pcb;
                }
            }
        }

        return newProc;
    }//chooseProcess

    /**
     * getRandomProcess
     *
     * selects a non-Blocked process at random from the ProcessTable.
     *
     * @return a reference to the ProcessControlBlock struct of the selected process
     * -OR- null if no non-blocked process exists
     */
    ProcessControlBlock getRandomProcess()
    {
        //Calculate a random offset into the m_processes list
        int offset = ((int)(Math.random() * 2147483647)) % m_processes.size();

        //Iterate until a non-blocked process is found
        ProcessControlBlock newProc = null;
        for(int i = 0; i < m_processes.size(); i++)
        {
            newProc = m_processes.get((i + offset) % m_processes.size());
            if ( ! newProc.isBlocked())
            {
                return newProc;
            }
        }//for

        return null;        // no processes are Ready
    }//getRandomProcess

    /**
     * scheduleNewProcess
     *
     * Selects a new non-blocked process to run and replaces the old running process.
     */
    public void scheduleNewProcess()
    {
        if (m_processes.size() == 0) {
            System.exit(0);
        }

        ProcessControlBlock proc = getRandomProcess();
        //ProcessControlBlock proc = chooseProcess();

        if (proc == null) {
            //Schedule an idle process.
            createIdleProcess();
            return;
        }

        //We are just scheduling ourself again don't bother context switching!
        if (proc == m_currProcess) {
            return;
        }

        //Save the CPU registers
        if (m_currProcess != null) {
            m_currProcess.save(m_CPU);
        }

        //Set this process as the new current process
        m_currProcess = proc;
        m_currProcess.restore(m_CPU);
    }//scheduleNewProcess

    /**
     * addProgram
     *
     * registers a new program with the simulated OS that can be used when the
     * current process makes an Exec system call.  (Normally the program is
     * specified by the process via a filename but this is a simulation so the
     * calling process doesn't actually care what program gets loaded.)
     *
     * @param prog  the program to add
     *
     */
    public void addProgram(Program prog)
    {
        m_programs.add(prog);
    }//addProgram


    /*======================================================================
     * Program Management Methods
     *----------------------------------------------------------------------
     */

    /**
     * createProcess
     *
     * Creates one process for the CPU.
     *
     * @param prog The program class to be loaded into memory.
     * @param allocSize The amount of memory to allocate for the program.
     * @return Whether or not the process was created.
     */
    public boolean createProcess(Program prog, int allocSize)
    {
        int base = allocBlock(allocSize);

        if (base == -1) {
            System.out.println("Error: Out of memory for new process of size " + allocSize + "!");
            return false;
        }

        int lim = base + allocSize;

        if (m_currProcess != null) {
            m_currProcess.save(m_CPU);
        }

        m_CPU.setBASE(base);
        m_CPU.setLIM(lim);
        m_CPU.setPC(0); //We are going to use a logical (not physical) PC
        m_CPU.setSP(allocSize-1); //Stack starts at the bottom and grows up.
        //The Stack is also logical

        m_currProcess = new ProcessControlBlock(m_nextProcessID++);
        m_processes.add(m_currProcess);

        //Write the program code to memory
        int[] progArray = prog.export();

        for (int progAddr=0; progAddr<progArray.length; ++progAddr ){
            m_MMU.write(base + progAddr, progArray[progAddr]);
        }

        //Load the registers in from the cpu
        m_currProcess.save(m_CPU);

        printMemAlloc();

        return true;
    }//createProcess


    /*======================================================================
     * Interrupt Handlers
     *----------------------------------------------------------------------
     */

    public void interruptIOReadComplete(int devID, int addr, int data) {
        Device dev = getDeviceInfo(devID).getDevice();
        ProcessControlBlock blocked = selectBlockedProcess(dev, SYSCALL_READ, addr);

        //Push the data and success code onto the stack.
        m_CPU.pushStack(data, blocked.getRegisters());
        m_CPU.pushStack(SYSCALL_RET_SUCCESS, blocked.getRegisters());

        //unblock the blocked process
        blocked.unblock();
    }

    public void interruptIOWriteComplete(int devID, int addr) {
        Device dev = getDeviceInfo(devID).getDevice();
        ProcessControlBlock blocked = selectBlockedProcess(dev, SYSCALL_WRITE, addr);

        //Push the success code onto the stack.
        m_CPU.pushStack(SYSCALL_RET_SUCCESS, blocked.getRegisters());

        //unblock the blocked process
        blocked.unblock();
    }

    /**
     * Handles Illegal Memory Access interrupts.
     *
     * @param addr The address which was attempted to be accessed
     */
    public void interruptIllegalMemoryAccess(int addr) {
        System.out.println("Error: Illegal Memory Access at addr " + addr);
        System.out.println("NOW YOU DIE!!!");
        System.exit(0);
    }

    /**
     * Handles Divide by Zero interrupts.
     */
    public void interruptDivideByZero() {
        System.out.println("Error: Divide by Zero");
        System.out.println("NOW YOU DIE!!!");
        System.exit(0);
    }

    /**
     * Handles Illegal Instruction interrupts.
     *
     * @param instr The instruction which caused the interrupt
     */
    public void interruptIllegalInstruction(int[] instr) {
        System.out.println("Error: Illegal Instruction:");
        System.out.println(instr[0] + ", " + instr[1] + ", " + instr[2] + ", " + instr[3]);
        System.out.println("NOW YOU DIE!!!");
        System.exit(0);
    }

    /**
     * Handles Clock interrupts.
     */
    public void interruptClock() {
        scheduleNewProcess();
    }


    /*======================================================================
     * System Calls
     *----------------------------------------------------------------------
     */

    /**
     * syscallExit
     *
     * Exits from the current process.
     */
    private void syscallExit() {
        removeCurrentProcess();
    }

    /**
     * syscallOutput
     *
     * Outputs the top number from the stack.
     */
    private void syscallOutput() {
        System.out.println("OUTPUT: " + m_CPU.popStack());
    }

    /**
     * syscallGetPID
     *
     * Pushes the PID to the stack.
     */
    private void syscallGetPID() {
        m_CPU.pushStack(m_currProcess.getProcessId());
    }

    /**
     * syscallOpen
     *
     * Open a device.
     */
    private void syscallOpen() {
        int devNum = m_CPU.popStack();
        DeviceInfo devInfo = getDeviceInfo(devNum);

        if (devInfo == null) {
            m_CPU.pushStack(SYSCALL_RET_DNE);
            return;
        }
        if (devInfo.containsProcess(m_currProcess)) {
            m_CPU.pushStack(SYSCALL_RET_ALREADY_OPEN);
            return;
        }
        if (! devInfo.device.isSharable() && ! devInfo.unused()) {

            //addr = -1 because this is not a read
            m_currProcess.block(m_CPU, devInfo.getDevice(), SYSCALL_OPEN, -1);
            devInfo.addProcess(m_currProcess);
            m_CPU.pushStack(SYSCALL_RET_SUCCESS);
            scheduleNewProcess();
            return;
        }

        //Associate the process with this device.
        devInfo.addProcess(m_currProcess);
        m_CPU.pushStack(SYSCALL_RET_SUCCESS);
    }

    /**
     * syscallClose
     *
     * Close a device.
     */
    private void syscallClose() {
        int devNum = m_CPU.popStack();
        DeviceInfo devInfo = getDeviceInfo(devNum);

        if (devInfo == null) {
            m_CPU.pushStack(SYSCALL_RET_DNE);
            return;
        }
        if (! devInfo.containsProcess(m_currProcess) ) {
            m_CPU.pushStack(SYSCALL_RET_NOT_OPEN);
            return;
        }

        //De-associate the process with this device.
        devInfo.removeProcess(m_currProcess);
        m_CPU.pushStack(SYSCALL_RET_SUCCESS);

        //Unblock next proc which wants to open this device
        ProcessControlBlock proc = selectBlockedProcess(devInfo.getDevice(), SYSCALL_OPEN, -1);
        if (proc != null) { 
            proc.unblock();
        }
    }

    /**
     * syscallRead
     *
     * Read from an open device.
     */
    private void syscallRead() {
        int addr = m_CPU.popStack();
        int devNum = m_CPU.popStack();
        DeviceInfo devInfo = getDeviceInfo(devNum);

        if (devInfo == null) {
            m_CPU.pushStack(SYSCALL_RET_DNE);
            return;
        }
        if (! devInfo.device.isAvailable() ) {

            //Push the addr, devNum, and syscall back onto the stack.
            m_CPU.pushStack(devNum);
            m_CPU.pushStack(addr);
            m_CPU.pushStack(SYSCALL_READ);

            //Decriment the PC counter so that the TRAP happens again
            m_CPU.setPC( m_CPU.getPC() - m_CPU.INSTRSIZE );

            //Try again later
            scheduleNewProcess();

            return;
        }
        if (! devInfo.containsProcess(m_currProcess) ) {
            m_CPU.pushStack(SYSCALL_RET_NOT_OPEN);
            return;
        }
        if (! devInfo.device.isReadable() ) {
            m_CPU.pushStack(SYSCALL_RET_WO);
            return;
        }

        //Start to read
        devInfo.getDevice().read(addr);

        m_currProcess.block(m_CPU, devInfo.getDevice(), SYSCALL_READ, addr);
        scheduleNewProcess();
    }

    /**
     * syscallWrite
     *
     * Write to an open device.
     */
    private void syscallWrite() {
        int value = m_CPU.popStack();
        int addr = m_CPU.popStack();
        int devNum = m_CPU.popStack();
        DeviceInfo devInfo = getDeviceInfo(devNum);

        if (devInfo == null) {
            m_CPU.pushStack(SYSCALL_RET_DNE);
            return;
        }
        if (! devInfo.device.isAvailable() ) {

            //Push the value, addr, devNum and syscall back onto the stack.
            m_CPU.pushStack(devNum);
            m_CPU.pushStack(addr);
            m_CPU.pushStack(value);
            m_CPU.pushStack(SYSCALL_WRITE);

            //Decriment the PC counter so that the TRAP happens again
            m_CPU.setPC( m_CPU.getPC() - m_CPU.INSTRSIZE );
            m_currProcess.save(m_CPU);

            //Try again later
            scheduleNewProcess();

            return;
        }
        if (! devInfo.containsProcess(m_currProcess) ) {
            m_CPU.pushStack(SYSCALL_RET_NOT_OPEN);
            return;
        }
        if (! devInfo.device.isWriteable() ) {
            m_CPU.pushStack(SYSCALL_RET_RO);
            return;
        }

        //Start to write
        devInfo.getDevice().write(addr, value);

        m_currProcess.block(m_CPU, devInfo.getDevice(), SYSCALL_WRITE, addr);
        scheduleNewProcess();
    }

    /**
     * syscallCoreDump
     *
     * Prints the registers and top three stack items, then exits the process.
     */
    private void syscallCoreDump() {

        System.out.println("\n\nCORE DUMP!");

        m_CPU.regDump();

        System.out.println("Top three stack items:");
        for (int i=0; i<3; ++i){
            if (m_CPU.validMemory(m_CPU.getSP() + 1 + m_CPU.getBASE())) {
                System.out.println(m_CPU.popStack());
            } else {
                System.out.println(" -- NULL -- ");
            }
        }
        syscallExit();
    }

    /**
     * syscallExec
     *
     * creates a new process.  The program used to create that process is chosen
     * semi-randomly from all the programs that have been registered with the OS
     * via {@link #addProgram}.  Limits are put into place to ensure that each
     * process is run an equal number of times.  If no programs have been
     * registered then the simulation is aborted with a fatal error.
     *
     */
    private void syscallExec()
    {
        //If there is nothing to run, abort.  This should never happen.
        if (m_programs.size() == 0)
        {
            System.err.println("ERROR!  syscallExec has no programs to run.");
            System.exit(-1);
        }

        //find out which program has been called the least and record how many
        //times it has been called
        int leastCallCount = m_programs.get(0).callCount;
        for(Program prog : m_programs)
        {
            if (prog.callCount < leastCallCount)
            {
                leastCallCount = prog.callCount;
            }
        }

        //Create a vector of all programs that have been called the least number
        //of times
        Vector<Program> cands = new Vector<Program>();
        for(Program prog : m_programs)
        {
            cands.add(prog);
        }

        //Select a random program from the candidates list
        Random rand = new Random();
        int pn = rand.nextInt(m_programs.size());
        Program prog = cands.get(pn);

        //Determine the address space size using the default if available.
        //Otherwise, use a multiple of the program size.
        int allocSize = prog.getDefaultAllocSize();
        if (allocSize <= 0)
        {
            allocSize = prog.getSize() * 2;
        }

        //Load the program into RAM
        if (createProcess(prog, allocSize)) {
            //Adjust the PC since it's about to be incremented by the CPU
            m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);
        }

    }//syscallExec



    /**
     * syscallYield
     *
     * Allow process to voluntarily move from Running to Ready.
     */
    private void syscallYield()
    {
        scheduleNewProcess();
    }//syscallYield


    /**
     * systemCall
     *
     * Occurs when TRAP is encountered in child process.
     */
    public void systemCall()
    {
        int syscallNum = m_CPU.popStack();

        switch (syscallNum) {
            case SYSCALL_EXIT:
                syscallExit();
                break;
            case SYSCALL_OUTPUT:
                syscallOutput();
                break;
            case SYSCALL_GETPID:
                syscallGetPID();
                break;
            case SYSCALL_OPEN:
                syscallOpen();
                break;
            case SYSCALL_CLOSE:
                syscallClose();
                break;
            case SYSCALL_READ:
                syscallRead();
                break;
            case SYSCALL_WRITE:
                syscallWrite();
                break;
            case SYSCALL_EXEC:
                syscallExec();
                break;
            case SYSCALL_YIELD:
                syscallYield();
                break;
            case SYSCALL_COREDUMP:
                syscallCoreDump();
                break;
        }
    }


    //======================================================================
    // Inner Classes
    //----------------------------------------------------------------------

    /**
     * class MemBlock
     *
     * This class contains relevant info about a memory block in RAM.
     *
     */
    private class MemBlock implements Comparable<MemBlock>
    {
        /** the address of the block */
        private int m_addr;
        /** the size of the block */
        private int m_size;

        /**
         * ctor does nothing special
         */
        public MemBlock(int addr, int size)
        {
            m_addr = addr;
            m_size = size;
        }

        /** accessor methods */
        public int getAddr() { return m_addr; }
        public int getSize() { return m_size; }

        /**
         * compareTo              
         *
         * compares this to another MemBlock object based on address
         */
        public int compareTo(MemBlock m)
        {
            return this.m_addr - m.m_addr;
        }

    }//class MemBlock


    /**
     * class ProcessControlBlock
     *
     * This class contains information about a currently active process.
     */
    private class ProcessControlBlock implements Comparable<ProcessControlBlock>
    {
        /**
         * a unique id for this process
         */
        private int processId = 0;

        /**
         * These are the process' current registers.  If the process is in the
         * "running" state then these are out of date
         */
        private int[] registers = null;

        /**
         * If this process is blocked a reference to the Device is stored here
         */
        private Device blockedForDevice = null;

        /**
         * If this process is blocked a reference to the type of I/O operation
         * is stored here (use the SYSCALL constants defined in SOS)
         */
        private int blockedForOperation = -1;

        /**
         * If this process is blocked reading from a device, the requested
         * address is stored here.
         */
        private int blockedForAddr = -1;

        /**
         * the time it takes to load and save registers, specified as a number
         * of CPU ticks
         */
        private static final int SAVE_LOAD_TIME = 30;

        /**
         * Used to store the system time when a process is moved to the Ready
         * state.
         */
        private int lastReadyTime = -1;

        /**
         * Used to store the number of times this process has been in the ready
         * state
         */
        private int numReady = 0;

        /**
         * Used to store the maximum starve time experienced by this process
         */
        private int maxStarve = -1;

        /**
         * Used to store the average starve time for this process
         */
        private double avgStarve = 0;

        /** This is the lowest amount of quantum a proc can have. It is also
         * the default they start with.
         */
        public static final int MIN_QUANTUM = 5;

        /** This is the most quantum a proc can have. It gains more quantum
         * by performing IO.
         */
        public static final int MAX_QUANTUM = 7;

        /** If the current starve time is longer than this, the proc
         * automatically gets a quantum of MAX_QUANTUM.
         */
        public static final int STARVED = 700;

        /**
         * How much of the proc's quantum has been used.
         */
        protected int quantum_used = 0;

        /**
         * The proc's current quantum. This number also represents the proc's
         * priority. Higher quantum proc's are more important.
         */
        protected int quantum = MIN_QUANTUM;

        /**
         * constructor
         *
         * @param pid        a process id for the process.  The caller is
         *                   responsible for making sure it is unique.
         */
        public ProcessControlBlock(int pid)
        {
            this.processId = pid;
            this.registers = new int[CPU.NUMREG];
        }

        /**
         * Moves a process to a new location up RAM. Will not work for moving
         * a process down in RAM.
         * @param newBase the new value of the base register for the process.
         * @return if the move was successfull.
         */
        public boolean move(int newBase)
        {
            int size = registers[CPU.LIM] - registers[CPU.BASE];
            int oldBase = registers[CPU.BASE];

            if (newBase + size > m_MMU.getSize()) {
                //This program is too large to fit into memory there.
                return false;
            }

            /*
            //Perform the block copy
            for (int i = 0; i < size; ++i) {
                m_MMU.write(newBase + i, m_MMU.read(registers[CPU.BASE] + i));
            }
            */

            int newPage = 
                (newBase & m_MMU.getPageMask()) >> m_MMU.getOffsetSize();

            int oldPage = 
                (oldBase & m_MMU.getPageMask()) >> m_MMU.getOffsetSize();

            int procPages = size / m_MMU.getPageSize();

            int endPage = oldPage + procPages; //Up to but not including this page

            int pageDelta = oldPage - newPage; //how far to move up.

            for (int virtPage = 0; virtPage < endPage - newPage; ++virtPage) 
            {
                if (virtPage < procPages) 
                {
                    newVirtPage = newPage + virtPage + pageDelta;
                }
                else 
                {
                    newVirtPage = newPage + virtPage - procPages;
                }
            }

            //Update the registers.
            int offset = newBase - oldBase;

            registers[CPU.BASE] = newBase;
            registers[CPU.LIM] += offset;

            if (this == m_currProcess) {
                restore(m_CPU); //Update the registers in the CPU
            }

            debugPrintln("Process " + processId + " has moved from " + oldBase + " to " + newBase);

            return true;

        }//move

        /**
         * @return the last time this process was put in the Ready state
         */
        public long getLastReadyTime()
        {
            return lastReadyTime;
        }

        /**
         * @return the current process' id
         */
        public int getProcessId()
        {
            return this.processId;
        }

        /**
         * @return the process' registers array
         */
        public int[] getRegisters() {
            return registers;
        }

        /**
         * save
         *
         * saves the current CPU registers into this.registers
         *
         * @param cpu  the CPU object to save the values from
         */
        public void save(CPU cpu)
        {
            //A context switch is expensive.  We simluate that here by 
            //adding ticks to m_CPU
            m_CPU.addTicks(SAVE_LOAD_TIME);

            //Save the registers
            int[] regs = cpu.getRegisters();
            this.registers = new int[CPU.NUMREG];
            for(int i = 0; i < CPU.NUMREG; i++)
            {
                this.registers[i] = regs[i];
            }

            //Assuming this method is being called because the process is moving
            //out of the Running state, record the current system time for
            //calculating starve times for this process.  If this method is
            //being called for a Block, we'll adjust lastReadyTime in the
            //unblock method.
            numReady++;
            lastReadyTime = m_CPU.getTicks();

        }//save

        /**
         * restore
         *
         * restores the saved values in this.registers to the current CPU's
         * registers
         *
         * @param cpu  the CPU object to restore the values to
         */
        public void restore(CPU cpu)
        {
            //A context switch is expensive.  We simluate that here by 
            //adding ticks to m_CPU
            m_CPU.addTicks(SAVE_LOAD_TIME);

            //Restore the register values
            int[] regs = cpu.getRegisters();
            for(int i = 0; i < CPU.NUMREG; i++)
            {
                regs[i] = this.registers[i];
            }

            //Record the starve time statistics
            int starveTime = m_CPU.getTicks() - lastReadyTime;
            if (starveTime > maxStarve)
            {
                maxStarve = starveTime;
            }
            double d_numReady = (double)numReady;
            avgStarve = avgStarve * (d_numReady - 1.0) / d_numReady;
            avgStarve = avgStarve + (starveTime * (1.0 / d_numReady));
        }//restore


        /**
         * getRegisterValue
         *
         * Retrieves the value of a process' register that is stored in this
         * object (this.registers).
         * 
         * @param idx the index of the register to retrieve.  Use the constants
         *            in the CPU class
         * @return one of the register values stored in in this object or -999
         *         if an invalid index is given 
         */
        public int getRegisterValue(int idx)
        {
            if ((idx < 0) || (idx >= CPU.NUMREG))
            {
                return -999;    // invalid index
            }

            return this.registers[idx];
        }//getRegisterValue

        /**
         * setRegisterValue
         *
         * Sets the value of a process' register that is stored in this
         * object (this.registers).  
         * 
         * @param idx the index of the register to set.  Use the constants
         *            in the CPU class.  If an invalid index is given, this
         *            method does nothing.
         * @param val the value to set the register to
         */
        public void setRegisterValue(int idx, int val)
        {
            if ((idx < 0) || (idx >= CPU.NUMREG))
            {
                return;    // invalid index
            }

            this.registers[idx] = val;
        }//setRegisterValue

        /**
         * overallAvgStarve
         *
         * @return the overall average starve time for all currently running
         *         processes
         *
         */
        public double overallAvgStarve()
        {
            double result = 0.0;
            int count = 0;
            for(ProcessControlBlock pi : m_processes)
            {
                if (pi.avgStarve > 0)
                {
                    result = result + pi.avgStarve;
                    count++;
                }
            }
            if (count > 0)
            {
                result = result / count;
            }

            return result;
        }//overallAvgStarve

        /**
         * toString       **DEBUGGING**
         *
         * @return a string representation of this class
         */
        public String toString()
        {
            //Print the Process ID and process state (READY, RUNNING, BLOCKED)
            String result = "Process id " + processId + " ";
            if (isBlocked())
            {
                result = result + "is BLOCKED for ";
                //Print device, syscall and address that caused the BLOCKED state
                if (blockedForOperation == SYSCALL_OPEN)
                {
                    result = result + "OPEN";
                }
                else
                {
                    result = result + "WRITE @" + blockedForAddr;
                }
                for(DeviceInfo di : m_devices)
                {
                    if (di.getDevice() == blockedForDevice)
                    {
                        result = result + " on device #" + di.getId();
                        break;
                    }
                }
                result = result + ": ";
            }
            else if (this == m_currProcess)
            {
                result = result + "is RUNNING: ";
            }
            else
            {
                result = result + "is READY: ";
            }

            //Print the register values stored in this object.  These don't
            //necessarily match what's on the CPU for a Running process.
            if (registers == null)
            {
                result = result + "<never saved>";
                return result;
            }

            for(int i = 0; i < CPU.NUMGENREG; i++)
            {
                result = result + ("r" + i + "=" + registers[i] + " ");
            }//for
            result = result + ("PC=" + registers[CPU.PC] + " ");
            result = result + ("SP=" + registers[CPU.SP] + " ");
            result = result + ("BASE=" + registers[CPU.BASE] + " ");
            result = result + ("LIM=" + registers[CPU.LIM] + " ");

            //Print the starve time statistics for this process
            result = result + "\n\t\t\t";
            result = result + " Max Starve Time: " + maxStarve;
            result = result + " Avg Starve Time: " + avgStarve;

            return result;
        }//toString


        /**
         * block
         *
         * blocks the current process to wait for I/O.  The caller is
         * responsible for calling {@link CPU#scheduleNewProcess}
         * after calling this method.
         *
         * @param cpu   the CPU that the process is running on
         * @param dev   the Device that the process must wait for
         * @param op    the operation that the process is performing on the
         *              device.  Use the SYSCALL constants for this value.
         * @param addr  the address the process is reading from (for SYSCALL_READ)
         * 
         */
        public void block(CPU cpu, Device dev, int op, int addr)
        {
            blockedForDevice = dev;
            blockedForOperation = op;
            blockedForAddr = addr;

        }//block

        /**
         * unblock
         *
         * moves this process from the Blocked (waiting) state to the Ready
         * state. 
         *
         */
        public void unblock()
        {
            //Reset the info about the block
            blockedForDevice = null;
            blockedForOperation = -1;
            blockedForAddr = -1;

            //Assuming this method is being called because the process is moving
            //from the Blocked state to the Ready state, record the current
            //system time for calculating starve times for this process.
            lastReadyTime = m_CPU.getTicks();

        }//unblock

        /**
         * isBlocked
         *
         * @return true if the process is blocked
         */
        public boolean isBlocked()
        {
            return (blockedForDevice != null);
        }//isBlocked

        /**
         * isBlockedForDevice
         *
         * Checks to see if the process is blocked for the given device,
         * operation and address.  If the operation is not an open, the given
         * address is ignored.
         *
         * @param dev   check to see if the process is waiting for this device
         * @param op    check to see if the process is waiting for this operation
         * @param addr  check to see if the process is reading from this address
         *
         * @return true if the process is blocked by the given parameters
         */
        public boolean isBlockedForDevice(Device dev, int op, int addr)
        {
            if ( (blockedForDevice == dev) && (blockedForOperation == op) )
            {
                if (op == SYSCALL_OPEN)
                {
                    return true;
                }

                if (addr == blockedForAddr)
                {
                    return true;
                }
            }//if

            return false;
        }//isBlockedForDevice

        /**
         * compareTo              
         *
         * compares this to another ProcessControlBlock object based on the BASE addr
         * register.  Read about Java's Collections class for info on
         * how this method can be quite useful to you.
         */
        public int compareTo(ProcessControlBlock pi)
        {
            return this.registers[CPU.BASE] - pi.registers[CPU.BASE];
        }

    }//class ProcessControlBlock

    /**
     * class DeviceInfo
     *
     * This class contains information about a device that is currently
     * registered with the system.
     */
    private class DeviceInfo
    {
        /** every device has a unique id */
        private int id;
        /** a reference to the device driver for this device */
        private Device device;
        /** a list of processes that have opened this device */
        private Vector<ProcessControlBlock> procs;

        /**
         * constructor
         *
         * @param d          a reference to the device driver for this device
         * @param initID     the id for this device.  The caller is responsible
         *                   for guaranteeing that this is a unique id.
         */
        public DeviceInfo(Device d, int initID)
        {
            this.id = initID;
            this.device = d;
            d.setId(initID);
            this.procs = new Vector<ProcessControlBlock>();
        }

        /** @return the device's id */
        public int getId()
        {
            return this.id;
        }

        /** @return this device's driver */
        public Device getDevice()
        {
            return this.device;
        }

        /** Register a new process as having opened this device */
        public void addProcess(ProcessControlBlock pi)
        {
            procs.add(pi);
        }

        /** Register a process as having closed this device */
        public void removeProcess(ProcessControlBlock pi)
        {
            procs.remove(pi);
        }

        /** Does the given process currently have this device opened? */
        public boolean containsProcess(ProcessControlBlock pi)
        {
            return procs.contains(pi);
        }

        /** @return a vector of ProcessControlBlocks which have the device open (or are blocked for it.) */
        public Vector<ProcessControlBlock> getPCBs() {
            return procs;
        }

        /** Is this device currently not opened by any process? */
        public boolean unused()
        {
            return procs.size() == 0;
        }

    }//class DeviceInfo

};//class SOS

