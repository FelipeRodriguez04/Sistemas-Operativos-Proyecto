package vmmanager;

import vmsimulation.BackingStore;
import vmsimulation.BitwiseToolbox;
import vmsimulation.MainMemory;
import vmsimulation.MemoryException;

public class VirtualMemoryManagerV1 {

    MainMemory memory;   // The main memory
    BackingStore disk;   // The disk
    Integer pageSize;    // Page size (in bytes)

    // Page table: for each virtual page, which frame it is in (-1 if not loaded)
    private int[] pageTable;
    private int numPages;
    private int numFrames;

    // Bits
    private int offsetBits;    // bits for offset within page
    private int physAddrBits;  // bits for physical address
    private int virtAddrBits;  // bits for virtual address

    // Next free frame to use (we never evict in V1)
    private int nextFreeFrame = 0;

    // Stats
    private int pageFaultCount = 0;
    private int transferredByteCount = 0;

    // log2(): Convenient function to compute the log2 of an integer;
    private int log2(int x) {
        return (int) (Math.log(x) / Math.log(2));
    }

    // Constructor
    public VirtualMemoryManagerV1(MainMemory memory,
                                  BackingStore disk,
                                  Integer pageSize) throws MemoryException {
        this.memory = memory;
        this.disk = disk;
        this.pageSize = pageSize;

        // Sizes
        int memSize = memory.size();
        int diskSize = disk.size();

        this.offsetBits = log2(pageSize);
        this.physAddrBits = log2(memSize);
        this.virtAddrBits = log2(diskSize);

        this.numFrames = memSize / pageSize;
        this.numPages = diskSize / pageSize;

        // Initialize page table: -1 means "not in memory"
        this.pageTable = new int[numPages];
        for (int i = 0; i < numPages; i++) {
            pageTable[i] = -1;
        }
    }

    // Helper: ensure a page is in memory, loading it on a page fault.
    // Returns the frame number.
    private int ensurePageInMemory(int pageNumber) throws MemoryException {
        int frame = pageTable[pageNumber];

        if (frame == -1) {
            // Page fault
            frame = nextFreeFrame;

            System.out.println("Bringing page " + pageNumber + " into frame " + frame);

            // Read page from disk
            byte[] pageData = disk.readPage(pageNumber);

            // Copy into main memory
            int baseAddr = frame * pageSize;
            for (int i = 0; i < pageSize; i++) {
                memory.writeByte(baseAddr + i, pageData[i]);
            }

            pageTable[pageNumber] = frame;
            nextFreeFrame++;
            pageFaultCount++;
            transferredByteCount += pageSize; // bytes moved from disk to RAM
        } else {
            System.out.println("Page " + pageNumber + " is in memory");
        }

        return frame;
    }

    // Given a virtual address (int), compute <page, offset>.
    private int getPageNumber(int virtualAddress) {
        // Virtual page = bits [offsetBits .. virtAddrBits-1]
        return BitwiseToolbox.extractBits(virtualAddress, offsetBits, virtAddrBits - 1);
    }

    private int getOffset(int virtualAddress) {
        // Offset = low offsetBits bits
        return BitwiseToolbox.extractBits(virtualAddress, 0, offsetBits - 1);
    }

    // Method to write a byte to memory given a virtual address
    public void writeByte(Integer fourByteBinaryString, Byte value) throws MemoryException {
        int va = fourByteBinaryString;

        int pageNumber = getPageNumber(va);
        int offset = getOffset(va);

        int frame = ensurePageInMemory(pageNumber);

        int physicalAddress = frame * pageSize + offset;

        memory.writeByte(physicalAddress, value.byteValue());

        String bitString = BitwiseToolbox.getBitString(physicalAddress, physAddrBits - 1);

        System.out.println("RAM: @" + bitString + " <-- " + value);
    }

    // Method to read a byte from memory given a virtual address
    public Byte readByte(Integer fourByteBinaryString) throws MemoryException {
        int va = fourByteBinaryString;

        int pageNumber = getPageNumber(va);
        int offset = getOffset(va);

        int frame = ensurePageInMemory(pageNumber);

        int physicalAddress = frame * pageSize + offset;

        byte value = memory.readByte(physicalAddress);

        String bitString = BitwiseToolbox.getBitString(physicalAddress, physAddrBits - 1);

        System.out.println("RAM: @" + bitString + " --> " + value);

        return value;
    }

    // Method to print all memory content
    public void printMemoryContent() throws MemoryException {
        int memSize = memory.size();

        for (int addr = 0; addr < memSize; addr++) {
            String addrBits = BitwiseToolbox.getBitString(addr, physAddrBits - 1);
            byte value = memory.readByte(addr);
            System.out.println(addrBits + ": " + value);
        }
    }

    // Method to print all disk content
    public void printDiskContent() throws MemoryException {
        // Disk is page-addressable
        int diskSize = disk.size();
        int pages = diskSize / pageSize;

        for (int p = 0; p < pages; p++) {
            byte[] pageData = disk.readPage(p);  // DO NOT count towards transferredByteCount
            System.out.print("PAGE " + p + ": ");
            for (int i = 0; i < pageSize; i++) {
                System.out.print(pageData[i]);
                if (i < pageSize - 1) {
                    System.out.print(",");
                }
            }
            System.out.println();
        }
    }

    // Method to write back all pages to disk
    public void writeBackAllPagesToDisk() throws MemoryException {
        for (int page = 0; page < numPages; page++) {
            int frame = pageTable[page];
            if (frame != -1) {
                // Page is in memory; write it back
                byte[] data = new byte[pageSize];
                int baseAddr = frame * pageSize;
                for (int i = 0; i < pageSize; i++) {
                    data[i] = memory.readByte(baseAddr + i);
                }
                disk.writePage(page, data);
                transferredByteCount += pageSize; // bytes moved from RAM to disk
            }
        }
    }

    // Method to retrieve the page fault count
    public int getPageFaultCount() {
        return pageFaultCount;
    }

    // Method to retrieve the number of bytes transferred between RAM and disk
    public int getTransferedByteCount() {
        return transferredByteCount;
    }
}
