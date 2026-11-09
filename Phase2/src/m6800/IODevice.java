package m6800;
//again simulating io device, use memory.mapIO(0x8000, myDevice)
//doesnt really do anything, will actually do stuff when made into 8 segment display/keypad
public interface IODevice {


    //called when cpu reads from address this device is mapped to.
    int read(int address);


    //called when cpu writes to an address this is mapped to
    void write(int address, int value);
}
