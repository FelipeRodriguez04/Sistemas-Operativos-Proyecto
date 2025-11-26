package vmmanager;

import vmsimulation.BitwiseToolbox;
import vmsimulation.MainMemory;
import vmsimulation.MemoryException;

public class VirtualMemoryManagerV0 {

    MainMemory memory;

    private int log2(int x) {
        return (int) (Math.log(x) / Math.log(2));
    }

    // Constructor
    public VirtualMemoryManagerV0(MainMemory memory) throws MemoryException {
        this.memory=memory;
    }

    public void writeByte(Integer fourByteBinaryString, Byte value) throws MemoryException {
        int memSize=memory.size();
        int addrBits=log2(memSize);
        int physicalAddress=BitwiseToolbox.extractBits(fourByteBinaryString, 0, addrBits-1);
        memory.writeByte(physicalAddress, value.byteValue());
        String addrBitString=BitwiseToolbox.getBitString(physicalAddress, addrBits-1);
        System.out.println("RAM write: @"+addrBitString+" <-- "+value);
    }

    public Byte readByte(Integer fourByteBinaryString) throws MemoryException {
        int memSize=memory.size();
        int addrBits=log2(memSize);
        int physicalAddress=BitwiseToolbox.extractBits(fourByteBinaryString, 0, addrBits-1);
        byte value=memory.readByte(physicalAddress);
        String addrBitString=BitwiseToolbox.getBitString(physicalAddress, addrBits-1);
        System.out.println("RAM read: @"+addrBitString+" --> "+value);
        return value;
    }

    public void printMemoryContent() throws MemoryException {
        int memSize=memory.size();
        int addrBits=log2(memSize);
        for (int addr=0; addr < memSize; addr++) {
            String addrBitString=BitwiseToolbox.getBitString(addr, addrBits-1);
            byte value=memory.readByte(addr);
            System.out.println(addrBitString+": "+value);
        }
    }
}

