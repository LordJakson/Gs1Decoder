package at.lord_jakson.gs1_decoder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Gs1Decoder implements Iterable<Gs1Decoder.Gs1Item>
{
    public static final char GS = 29;

    private enum Gs1Decoder_CodeType
    {
        NONE(0), GS(1), BRACKET_ROUND(2), BRACKET_SQUARE(3);

        private final int value;

        Gs1Decoder_CodeType(int value)
        {
            this.value = value;
        }

        public int getValue()
        {
            return value;
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


    private Gs1DataBuffer buffer;
    private Gs1Decoder_CodeType codeType;
    private final List<Gs1Item> items;
    private static final Pattern pattern_numeric = Pattern.compile("^\\d+$");

    public Gs1Decoder()
    {
        this.items = new ArrayList<>();
    }

    public static Gs1Decoder_CodeType getCodeType(Gs1DataBuffer buffer)
    {
        char temp = buffer.readChar();
        if (temp == GS)
        {
            return Gs1Decoder_CodeType.GS;
        }
        else if (temp == '(')
        {
            return Gs1Decoder_CodeType.BRACKET_ROUND;
        }
        else if (temp == '[')
        {
            return Gs1Decoder_CodeType.BRACKET_SQUARE;
        }

        return Gs1Decoder_CodeType.NONE;
    }

    public static Gs1Decoder_CodeType getCodeType(String code)
    {
        Gs1DataBuffer buffer = new Gs1DataBuffer(code);
        return getCodeType(buffer);
    }

    private static boolean isNumeric(String strNum)
    {
        if (strNum == null)
        {
            return false;
        }
        return pattern_numeric.matcher(strNum).matches();
    }

    private static boolean isNumeric(char charNum)
    {
        return charNum >= 48 && charNum <= 57;
    }

    private Gs1Data_Item readApplicationIdentifier_Gs() throws Gs1Exception
    {
        Gs1DataFields.Field_Result temp_item = Gs1DataFields.root.findItem(buffer);
        String ai = temp_item.data;
        if (temp_item.fieldResult != null && temp_item.fieldResult.valueType() == Gs1DataField_Type.GS_ITEM)
        {
            Gs1DataFields.Field_Item item = (Gs1DataFields.Field_Item) temp_item.fieldResult;
            ai = item.applicationIdentifier;
            int decimals = 0;

            if (item.varDec)
            {
                char temp_char = buffer.readChar();
                ai += temp_char;

                if (isNumeric(temp_char))
                {
                    decimals = temp_char - 48;
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

    private Gs1Data_Item readApplicationIdentifier_Bracket(char endBracket) throws Gs1Exception
    {
        String ai = buffer.readDataVar(endBracket);
        Gs1DataFields.Field_Result temp_item = Gs1DataFields.root.findItem(ai);
        if (temp_item.fieldResult != null && temp_item.fieldResult.valueType() == Gs1DataField_Type.GS_ITEM)
        {
            Gs1DataFields.Field_Item item = (Gs1DataFields.Field_Item) temp_item.fieldResult;
            ai = item.applicationIdentifier;
            int decimals = 0;

            if (item.varDec)
            {
                if (temp_item.data == null || temp_item.data.isEmpty())
                {
                    throw new Gs1Exception(Gs1Exception.INVALID_APPLICATION_IDENTIFIER, ai);
                }

                char temp_char = temp_item.data.charAt(0);
                ai += temp_char;

                if (isNumeric(temp_char))
                {
                    decimals = temp_char - 48;
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

    private Gs1Data_Item readApplicationIdentifier() throws Gs1Exception
    {
        switch (codeType)
        {
            case GS:
                return readApplicationIdentifier_Gs();
            case BRACKET_ROUND:
                return readApplicationIdentifier_Bracket(')');
            case BRACKET_SQUARE:
                return readApplicationIdentifier_Bracket(']');
        }
        return null;
    }

    private String readData(int dataLen)
    {
        switch (codeType)
        {
            case GS:
                return dataLen < 0 ? buffer.readDataVar(GS) : buffer.readDataFix(dataLen);
            case BRACKET_ROUND:
                return buffer.readDataVar('(');
            case BRACKET_SQUARE:
                return buffer.readDataVar('[');
        }
        return null;
    }

    public Gs1Decoder decodeCode(String code) throws Gs1Exception
    {
        buffer = new Gs1DataBuffer(code);
        codeType = getCodeType(buffer);
        if (codeType != Gs1Decoder_CodeType.NONE)
        {
            while (buffer.notEod())
            {
                Gs1Data_Item ai_field = readApplicationIdentifier();
                if(ai_field != null)
                {
                    String data = readData(ai_field.varLen ? -1 : ai_field.dataLen);
                    if(data == null)
                    {
                        throw new Gs1Exception(Gs1Exception.INVALID_DATA, "[" + ai_field.applicationIdentifier + "]");
                    }

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
                else
                {
                    throw new Gs1Exception(Gs1Exception.UNKNOWN_APPLICATION_IDENTIFIER, "");
                }
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
