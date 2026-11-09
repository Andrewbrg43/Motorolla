package m6800;

//simulates io device for testing, output only peripheral, ioport command maps one of these at 0x8000
public class DemoPort implements IODevice {

    @Override
    public int read(int address) {
        return 0xFF;    // output only
    }

    @Override
    public void write(int address, int value) {
        System.out.println(String.format("   [DemoPort $%04X] CPU wrote $%02X", address, value));
    }
}
