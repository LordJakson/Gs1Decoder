package at.lord_jakson.gs1_decoder;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Gs1Decoder implements Iterable<Gs1Decoder.Gs1Item>
{
    public static final byte GS = 29;
    public static final char GS_CHAR = 29;

    public static class Gs1Exception extends Exception
    {
        private static final long serialVersionUID = -1743475467298083833L;
        private final int code;

        public static final int UNKNOWN_APPLICATION_IDENTIFIER = 1;
        public static final int INVALID_APPLICATION_IDENTIFIER = 2;
        public static final int INVALID_DATA = 3;
        public static final int INVALID_DECIMAL = 4;

        private static String generateErrorMessage(int errorCode, String data)
        {
            switch (errorCode)
            {
                case UNKNOWN_APPLICATION_IDENTIFIER:
                    return String.format("Unknown ApplicationIdentifier '%s'", data);

                case INVALID_APPLICATION_IDENTIFIER:
                    return String.format("Invalid ApplicationIdentifier '%s'", data);

                case INVALID_DATA:
                    return String.format("Invalid Data '%s'", data);

                case INVALID_DECIMAL:
                    return String.format("Invalid Decimal '%s'", data);
            }

            return "Unknown ErrorCode";
        }

        public Gs1Exception(int errorCode, String data)
        {
            super(generateErrorMessage(errorCode, data));
            this.code = errorCode;
        }

        public int getCode()
        {
            return this.code;
        }
    }

    private static class Gs1Data_Item
    {
        public final String applicationIdentifier;
        public final String title;
        public final String regEx;
        public final int decimal;
        public final int dataLen;
        public final boolean varLen;

        public Gs1Data_Item(String applicationIdentifier, String title, String regEx, int decimal, int dataLen, boolean varLen)
        {
            this.applicationIdentifier = applicationIdentifier;
            this.title = title;
            this.regEx = regEx;
            this.decimal = decimal;
            this.dataLen = dataLen;
            this.varLen = varLen;
        }
    }

    public static class Gs1Item
    {
        public final String applicationIdentifier;
        public final String title;
        public final String data;

        public Gs1Item(String applicationIdentifier, String title, String data)
        {
            this.applicationIdentifier = applicationIdentifier;
            this.title = title;
            this.data = data;
        }

        @Override
        public String toString()
        {
            return applicationIdentifier + ":" + title + ":" + data;
        }
    }

    public Iterator<Gs1Item> iterator()
    {
        return new Iterator<Gs1Item>()
        {
            private int currentIndex = 0;

            @Override
            public boolean hasNext()
            {
                return currentIndex < items.size() && items.get(currentIndex) != null;
            }

            @Override
            public Gs1Item next()
            {
                return items.get(currentIndex++);
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        };
    }


    private byte[] array_buffer;
    private int array_pos;
    private final List<Gs1Item> items;
    private static final Pattern pattern_numeric = Pattern.compile("^\\d+$");

    public Gs1Decoder()
    {
        this.items = new ArrayList<>();
    }

    public static boolean isGS1Code(byte[] array)
    {
        return (array.length > 0 && array[0] == GS);
    }

    public static boolean isGS1Code(String code)
    {
        if (!code.isEmpty())
        {
            return isGS1Code(code.getBytes(StandardCharsets.US_ASCII));
        }
        return false;
    }

    private static boolean isNumeric(String strNum)
    {
        if (strNum == null)
        {
            return false;
        }
        return pattern_numeric.matcher(strNum).matches();
    }

    private Gs1Data_Item readApplicationIdentifier() throws Gs1Exception
    {
        String ai = new String(array_buffer, array_pos, 2, StandardCharsets.US_ASCII);
        array_pos += 2;
        Gs1DataField_Type temp_item = Gs1DataFields.root.getItem(ai);
        if (temp_item != null)
        {
            while (temp_item != null && temp_item.valueType() == Gs1DataField_Type.GS_LIST)
            {
                String ai_sub = new String(array_buffer, array_pos, 1, StandardCharsets.US_ASCII);

                ai += ai_sub;
                array_pos += 1;

                temp_item = ((Gs1DataFields.Field_List) temp_item).getItem(ai_sub);
            }
        }

        if (temp_item != null && temp_item.valueType() == Gs1DataField_Type.GS_ITEM)
        {
            Gs1DataFields.Field_Item item = (Gs1DataFields.Field_Item) temp_item;
            ai = item.applicationIdentifier;
            int decimals = 0;

            if (item.varDec)
            {
                int temp_int = array_buffer[array_pos];
                ai += (char) temp_int;
                array_pos += 1;

                if (temp_int >= 48 && temp_int <= 57)
                {
                    decimals = temp_int - 48;
                }
            }

            return new Gs1Data_Item(ai, item.title, item.regEx, decimals, item.dataLen, item.varLen);
        }

        if (!isNumeric(ai))
        {
            throw new Gs1Exception(Gs1Exception.INVALID_APPLICATION_IDENTIFIER, ai);
        }

        throw new Gs1Exception(Gs1Exception.UNKNOWN_APPLICATION_IDENTIFIER, ai);
    }

    private String readDataFix(int dataLen)
    {
        dataLen = Math.min(dataLen, array_buffer.length - array_pos);
        String result = new String(array_buffer, array_pos, dataLen, StandardCharsets.US_ASCII);
        array_pos += dataLen;
        return result;
    }

    private String readDataVar()
    {
        int temp_pos = array_pos;
        while (temp_pos < array_buffer.length && array_buffer[temp_pos] != GS)
        {
            temp_pos += 1;
        }

        int dataLen = temp_pos - array_pos;
        String result = new String(array_buffer, array_pos, dataLen, StandardCharsets.US_ASCII);
        array_pos += dataLen + 1;
        return result;
    }

    public Gs1Decoder decodeCode(String code) throws Gs1Exception
    {
        array_buffer = code.getBytes(StandardCharsets.US_ASCII);
        if (isGS1Code(array_buffer))
        {
            array_pos = 1;
            while (array_pos < array_buffer.length)
            {
                Gs1Data_Item ai_field = readApplicationIdentifier();
                String data = ai_field.varLen ? readDataVar() : readDataFix(ai_field.dataLen);

                Matcher matcher = Pattern.compile(ai_field.regEx).matcher(ai_field.applicationIdentifier + data);
                if (!matcher.matches())
                {
                    throw new Gs1Exception(Gs1Exception.INVALID_DATA, "[" + ai_field.applicationIdentifier + "]" + data);
                }

                if (ai_field.decimal > 0)
                {
                    if (ai_field.decimal < data.length())
                    {
                        data = new StringBuffer(data).insert(data.length() - ai_field.decimal, ".").toString();
                    }
                    else
                    {
                        throw new Gs1Exception(Gs1Exception.INVALID_DECIMAL, "" + ai_field.decimal);
                    }
                }

                items.add(new Gs1Item(ai_field.applicationIdentifier, ai_field.title, data));
            }
            return this;
        }
        return null;
    }

    public int size()
    {
        return items.size();
    }

    public Gs1Item getItem(int index)
    {
        return items.get(index);
    }

    public Gs1Item getItemByAi(String applicationIdentifier)
    {
        for (Gs1Item item : items)
        {
            if (item.applicationIdentifier.equals(applicationIdentifier))
            {
                return item;
            }
        }
        return null;
    }

    public Gs1Item getItemByTitle(String title)
    {
        for (Gs1Item item : items)
        {
            if (item.title.equals(title))
            {
                return item;
            }
        }
        return null;
    }

    @Override
    public String toString()
    {
        StringBuilder strBuilder = new StringBuilder();

        for (Gs1Item item : items)
        {
            strBuilder.append(item.toString()).append("\n");
        }

        return strBuilder.toString();
    }

}
