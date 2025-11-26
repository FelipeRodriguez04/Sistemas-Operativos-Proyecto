package vmmanager;

import vmsimulation.BackingStore;
import vmsimulation.BitwiseToolbox;
import vmsimulation.MainMemory;
import vmsimulation.MemoryException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

public class VirtualMemoryManagerV4 {

    MainMemory memory;    // The main memory
    BackingStore disk;    // The disk
    Integer pageSize;    // Page size

    private int log2(int x) {
        return (int) (Math.log(x) / Math.log(2));
    }

    // Constructor
    public VirtualMemoryManagerV4(MainMemory memory,
                                  BackingStore disk,
                                  Integer pageSize) throws MemoryException {
        this.memory = memory;
        this.disk = disk;
        this.pageSize = pageSize;

        // TO AUGMENT, MOST LIKELY
    }

    // Method to write a byte to memory given a virtual address
    public void writeByte(Integer fourByteBinaryString, Byte value) throws MemoryException {

    }


    // Method to read a byte to memory given a virtual address
    public Byte readByte(Integer fourByteBinaryString) throws MemoryException {
        int algunNumeroApropiado = 0;
        byte valInAddr = memory.readByte(algunNumeroApropiado);

        return valInAddr; // MUST RETURN THE VALUE THAT WAS READ INSTEAD OF JUST ZERO
    }


    // Method to print all memory content
    public void printMemoryContent() throws MemoryException {

    }

    // Method to print all disk content
    public void printDiskContent() throws MemoryException {

    }

    // Method to write back all pages to disk
    public void writeBackAllPagesToDisk() throws MemoryException {

    }

    // Method to retrieve the page fault count
    public int getPageFaultCount() {
        // TO IMPLEMENT
        return 0; // MUST RETURN THE NUMBER OF PAGE FAULTS INSTEAD OF JUST ZERO
    }

    // Method to retrieve the number of bytes transfered between RAM and disk
    public int getTransferedByteCount() {
        return 0; // MUST RETURN THE NUMBER OF BYTES TRANSFERRED INSTEAD OF JUST ZERO
    }

    public void ramToDiskTransferAccumulatedBytes() {

    }

    public void increaseFaultNum() {

    }
}




