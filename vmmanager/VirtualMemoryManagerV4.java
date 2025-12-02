package vmmanager;

import vmsimulation.BackingStore;
import vmsimulation.MainMemory;
import vmsimulation.MemoryException;

public class VirtualMemoryManagerV4 {

    MainMemory memory;    
    BackingStore disk;   
    Integer pageSize;     

    private int[] pageFrame;
    private boolean[] valid;
    private boolean[] dirty;
    private int[] framePage;

    private long[] lastAccessTime; 
    private long accessCounter=0; 

    private int numFrames;
    private int numPages;

    private int pageFaultCount=0;
    private int transferredBytes=0;

    private int log2(int x) {
        return (int) (Math.log(x) / Math.log(2));
    }

    public VirtualMemoryManagerV4(MainMemory memory,
                                  BackingStore disk,
                                  Integer pageSize) throws MemoryException {
        this.memory=memory;
        this.disk=disk;
        this.pageSize=pageSize;

        int memSize=memory.size();
        int diskSize=disk.size();

        this.numFrames=memSize / pageSize;
        this.numPages=diskSize / pageSize;

        this.pageFrame=new int[numPages];
        this.valid=new boolean[numPages];
        this.dirty=new boolean[numPages];
        this.framePage=new int[numFrames];
        this.lastAccessTime=new long[numPages];

        for (int p=0; p<numPages; p++) {
            pageFrame[p]=-1;
            valid[p]=false;
            dirty[p]=false;
            lastAccessTime[p]=0;
        }
        for (int f=0; f<numFrames; f++) {
            framePage[f]=-1;
        }
    }

    public void writeByte(Integer fourByteBinaryString, Byte value) throws MemoryException {
        int virtualAddress=fourByteBinaryString;

        int pageNumber=virtualAddress / pageSize;
        int offset=virtualAddress % pageSize;

        if (pageNumber < 0 || pageNumber >= numPages) {
            throw new MemoryException("Invalid virtual address");
        }

        int frame=ensurePageInMemory(pageNumber);
        accessCounter++;
        lastAccessTime[pageNumber]=accessCounter;
        int physicalAddr=frame * pageSize + offset;
        memory.writeByte(physicalAddr, value);
        dirty[pageNumber]=true;
        int addrBits=log2(memory.size());
        String addrStr=toBinary(physicalAddr, addrBits);
        System.out.println("RAM: @" + addrStr + " <-- " + value);
    }

    public Byte readByte(Integer fourByteBinaryString) throws MemoryException {
        int virtualAddress=fourByteBinaryString;
        int pageNumber=virtualAddress / pageSize;
        int offset=virtualAddress % pageSize;
        if (pageNumber < 0 || pageNumber >= numPages) {
            throw new MemoryException("Invalid virtual address");
        }

        int frame=ensurePageInMemory(pageNumber);
        accessCounter++;
        lastAccessTime[pageNumber]=accessCounter;
        int physicalAddr=frame * pageSize + offset;
        byte valInAddr=memory.readByte(physicalAddr);
        int addrBits=log2(memory.size());
        String addrStr=toBinary(physicalAddr, addrBits);
        System.out.println("RAM: @" + addrStr + " --> " + valInAddr);
        return valInAddr;
    }

    private int ensurePageInMemory(int pageNumber) throws MemoryException {
        if (valid[pageNumber]) {
            System.out.println("Page " + pageNumber + " is in memory");
            return pageFrame[pageNumber];
        }
        increaseFaultNum();

        int frame=findFreeFrame();
        if (frame==-1) {
            int victimPage=-1;
            long oldestAccessTime=Long.MAX_VALUE;
            for (int p=0; p<numPages; p++) {
                if (valid[p]) {
                    if (lastAccessTime[p] < oldestAccessTime) {
                        oldestAccessTime = lastAccessTime[p];
                        victimPage = p;
                    }
                }
            }

            if (victimPage == -1) {
                throw new MemoryException("Internal LRU error: No valid victim page found.");
            }

            frame=pageFrame[victimPage];
            if (dirty[victimPage]) {
                System.out.println("Evicting page " + victimPage);
                writePageToDisk(victimPage, frame);
            } 
            else {
                System.out.println("Evicting page " + victimPage + " (NOT DIRTY)");
            }

            valid[victimPage]=false;
            dirty[victimPage]=false;
            pageFrame[victimPage]=-1;
        }

        System.out.println("Bringing page " + pageNumber + " into frame " + frame);
        readPageFromDisk(pageNumber, frame);
        pageFrame[pageNumber]=frame;
        valid[pageNumber]=true;
        dirty[pageNumber]=false;
        framePage[frame]=pageNumber;
        return frame;
    }
    
    public void printMemoryContent() throws MemoryException {
        int memSize=memory.size();
        int addrBits=log2(memSize);
        for (int addr=0; addr < memSize; addr++) {
            byte val=memory.readByte(addr);
            String addrStr=toBinary(addr, addrBits);
            System.out.println(addrStr + ": " + val);
        }
    }

    public void printDiskContent() throws MemoryException {
        for (int p=0; p < numPages; p++) {
            StringBuilder sb=new StringBuilder();
            sb.append("PAGE ").append(p).append(": ");
            byte[] pageData=disk.readPage(p); 

            for (int i = 0; i < pageSize; i++) {
                byte val = pageData[i];
                sb.append(val);
                if (i < pageSize - 1) {
                    sb.append(",");
                }
            }
            System.out.println(sb.toString());
        }
    }

    public void writeBackAllPagesToDisk() throws MemoryException {
        for (int page=0; page < numPages; page++) {
            if (valid[page] && dirty[page]) {
                int frame=pageFrame[page];
                writePageToDisk(page, frame);
                dirty[page]=false;
            }
        }
    }

    public int getPageFaultCount() {
        return pageFaultCount;
    }

    public int getTransferedByteCount() {
        return transferredBytes;
    }

    public void ramToDiskTransferAccumulatedBytes() {
        transferredBytes += pageSize;
    }

    public void increaseFaultNum() {
        pageFaultCount++;
    }

    private int findFreeFrame() {
        for (int f = 0; f < numFrames; f++) {
            if (framePage[f] == -1) {
                return f;
            }
        }
        return -1;
    }

    private void readPageFromDisk(int page, int frame) throws MemoryException {
        int memBase=frame * pageSize;
        byte[] pageData=disk.readPage(page); 
        for (int i=0; i < pageSize; i++) {
            memory.writeByte(memBase + i, pageData[i]);
        }
        ramToDiskTransferAccumulatedBytes();
    }

    private void writePageToDisk(int page, int frame) throws MemoryException {
        int memBase=frame * pageSize;
        byte[] pageData=new byte[pageSize];

        for (int i=0; i < pageSize; i++) {
            pageData[i]=memory.readByte(memBase + i);
        }
        disk.writePage(page, pageData); 
        ramToDiskTransferAccumulatedBytes();
    }

    private String toBinary(int value, int bits) {
        String s=Integer.toBinaryString(value);
        if (s.length()<bits) {
            StringBuilder sb=new StringBuilder();
            for (int i=s.length(); i < bits; i++) {
                sb.append('0');
            }
            sb.append(s);
            return sb.toString();
        }
        return s;
    }
}