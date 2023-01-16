package at.lord_jakson.gs1_decoder;

import java.nio.charset.StandardCharsets;

public class Gs1DataBuffer
{
    private final byte[] array_buffer;
    private int array_pos;

    public Gs1DataBuffer(String data)
    {
        this.array_buffer = data.getBytes(StandardCharsets.US_ASCII);
        this.array_pos = 0;
    }

    public String readDataFix(int dataLen)
    {
        dataLen = Math.min(dataLen, this.array_buffer.length - this.array_pos);
        String result = new String(this.array_buffer, this.array_pos, dataLen, StandardCharsets.US_ASCII);
        this.array_pos += dataLen;
        return result;
    }

    public String readDataVar(char untilChar)
    {
        int temp_pos = this.array_pos;
        while (temp_pos < this.array_buffer.length && this.array_buffer[temp_pos] != untilChar)
        {
            temp_pos += 1;
        }

        int dataLen = temp_pos - this.array_pos;
        String result = new String(this.array_buffer, this.array_pos, dataLen, StandardCharsets.US_ASCII);
        this.array_pos += dataLen + 1;
        return result;
    }

    public char readChar()
    {
        if (notEod())
        {
            char result = (char)this.array_buffer[this.array_pos];
            this.array_pos += 1;
            return result;
        }
        return 0;
    }

    public int getArrayPos()
    {
        return this.array_pos;
    }

    public boolean notEod()
    {
        return this.array_pos < this.array_buffer.length;
    }
}
