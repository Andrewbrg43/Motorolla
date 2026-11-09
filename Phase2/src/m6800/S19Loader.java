package m6800;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

//reads 19 fle and copies it to memory
public class S19Loader {

    //shows what happened during load
    public static class Result {
        public int bytesLoaded = 0;
        public int bytesSkipped = 0;
        public int startAddress = -1;
        public int firstSkippedAddress = -1;
    }


    //loads file into memory
    public static Result load(String filename, Memory memory, boolean asRom) throws IOException {
        Result result = new Result();

        BufferedReader reader = new BufferedReader(new FileReader(filename));
        try {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.length() == 0) {
                    continue;
                }
                if (line.charAt(0) != 'S' && line.charAt(0) != 's') {
                    throw new IOException("Line " + lineNumber + ": does not start with 'S'");
                }
                if (line.length() < 4) {
                    throw new IOException("Line " + lineNumber + ": record is too short");
                }

                char type = line.charAt(1);

                if (type == '1') {
                    loadDataRecord(line, lineNumber, memory, asRom, result);
                } else if (type == '9') {
                    result.startAddress = parseHex(line, 4, 4, lineNumber);
                }

            }
        } finally {
            reader.close();
        }

        return result;
    }

    //handles a s1 line
    private static void loadDataRecord(String line, int lineNumber, Memory memory,
                                       boolean asRom, Result result) throws IOException {

        int byteCount = parseHex(line, 2, 2, lineNumber);  // characters 2-3
        int address = parseHex(line, 4, 4, lineNumber);    // characters 4-7


        int dataBytes = byteCount - 3;
        if (dataBytes < 0) {
            throw new IOException("Line " + lineNumber + ": byte count is too small");
        }
        if (line.length() < 8 + dataBytes * 2) {
            throw new IOException("Line " + lineNumber + ": record is shorter than its byte count says");
        }

        for (int i = 0; i < dataBytes; i++) {
            int value = parseHex(line, 8 + i * 2, 2, lineNumber);
            int target = (address + i) & 0xFFFF;

            if (memory.loadByte(target, value, asRom)) {
                result.bytesLoaded++;
            } else {
                result.bytesSkipped++;
                if (result.firstSkippedAddress == -1) {
                    result.firstSkippedAddress = target;
                }
            }
        }
    }

    //converts hex characters into number
    private static int parseHex(String line, int start, int length, int lineNumber) throws IOException {
        if (line.length() < start + length) {
            throw new IOException("Line " + lineNumber + ": record is too short");
        }
        try {
            return Integer.parseInt(line.substring(start, start + length), 16);
        } catch (NumberFormatException e) {
            throw new IOException("Line " + lineNumber + ": bad hex digits '"
                    + line.substring(start, start + length) + "'");
        }
    }
}
