package at.lord_jakson.gs1_decoder;

import java.util.HashMap;
import java.util.Map;

public class Gs1DataFields
{
    private static final String VALID_CHARS = "[\\x21-\\x22\\x25-\\x2F\\x30-\\x39\\x3A-\\x3F\\x41-\\x5A\\x5F\\x61-\\x7A]";
    // x21-x22 = ! "
    // x25-x2F = % & ' ( ) * + , - . /
    // x30-x39 = 0 1 2 3 4 5 6 7 8 9
    // x3A-x3F = : ; < = > ?
    // x41-x5A = A-Z
    // x5F     = _
    // x61-x7A = a-z{

    public static class Field_Result
    {
        public final Gs1DataField_Type fieldResult;
        public final String data;

        public Field_Result(Gs1DataField_Type fieldResult, String data)
        {
            this.fieldResult = fieldResult;
            this.data = data;
        }
    }

    public static class Field_Item implements Gs1DataField_Type
    {
        private final Field_List parentList;
        public final String applicationIdentifier;
        public final String title;
        public final String regEx;
        private final int dataLen;

        public Field_Item(Field_List parentList, String applicationIdentifier, String title, String regEx, int dataLen)
        {
            this.parentList = parentList;
            this.applicationIdentifier = applicationIdentifier;
            this.title = title;
            this.regEx = regEx;
            this.dataLen = dataLen;
        }

        public int getDataLen()
        {
            if (this.dataLen > 0)
            {
                return this.dataLen;
            }
            else if (this.parentList != null)
            {
                return this.parentList.getDataLen();
            }
            return 0;
        }

        public boolean getVarDec()
        {
            if (this.parentList != null)
            {
                return this.parentList.getVarDec();
            }
            return false;
        }

        @Override
        public int valueType()
        {
            return GS_ITEM;
        }
    }

    public static class Field_List implements Gs1DataField_Type
    {
        private final Field_List parentList;
        private final Map<String, Gs1DataField_Type> items;
        private int dataLen;
        private boolean varDec;

        public Field_List(Field_List parentList, int dataLen, boolean varDec)
        {
            this.items = new HashMap<>();
            this.parentList = parentList;
            this.dataLen = dataLen;
            this.varDec = varDec;
        }

        Field_List addList(String key, int dataLen, boolean varDec)
        {
            Field_List result = new Field_List(this, dataLen, varDec);
            items.put(key, result);
            return result;
        }

        Field_List addList(String key)
        {
            Field_List result = new Field_List(this, 0, false);
            items.put(key, result);
            return result;
        }

        void addItem(String key, String applicationIdentifier, String title, String regEx, int dataLen)
        {
            Field_Item result = new Field_Item(this, applicationIdentifier, title, regEx, dataLen);
            items.put(key, result);
        }

        void addItem(String key, String applicationIdentifier, String title, String regEx)
        {
            Field_Item result = new Field_Item(this, applicationIdentifier, title, regEx, 0);
            items.put(key, result);
        }

        public Gs1DataField_Type getItem(String key)
        {
            return items.get(key);
        }

        public int getDataLen()
        {
            if (this.dataLen > 0)
            {
                return this.dataLen;
            }
            else if (this.parentList != null)
            {
                return this.parentList.getDataLen();
            }
            return 0;
        }

        public boolean getVarDec()
        {
            if (this.varDec)
            {
                return true;
            }
            else if (this.parentList != null)
            {
                return this.parentList.getVarDec();
            }
            return false;
        }

        @Override
        public int valueType()
        {
            return GS_LIST;
        }
    }

    public static class Field_ListRoot extends Field_List
    {
        public Field_ListRoot()
        {
            super(null, 0, false);
        }

        public Field_Result findItem(Gs1DataBuffer buffer)
        {
            String ai = buffer.readDataFix(2);
            Gs1DataField_Type temp_item = Gs1DataFields.root.getItem(ai);
            if (temp_item != null)
            {
                while (temp_item != null && temp_item.valueType() == Gs1DataField_Type.GS_LIST)
                {
                    String ai_sub = buffer.readDataFix(1);
                    ai += ai_sub;
                    temp_item = ((Gs1DataFields.Field_List) temp_item).getItem(ai_sub);
                }
            }

            return new Field_Result(temp_item, ai);
        }

        public Field_Result findItem(String key)
        {
            StringBuilder strBuilder = new StringBuilder(key);
            String ai = strBuilder.substring(0, 2);
            strBuilder.delete(0, 2);
            Gs1DataField_Type temp_item = Gs1DataFields.root.getItem(ai);
            if (temp_item != null)
            {
                while (temp_item != null && temp_item.valueType() == Gs1DataField_Type.GS_LIST)
                {
                    if (strBuilder.length() <= 0)
                    {
                        return new Field_Result(null, key);
                    }
                    String ai_sub = "" + strBuilder.charAt(0);
                    strBuilder.deleteCharAt(0);
                    temp_item = ((Gs1DataFields.Field_List) temp_item).getItem(ai_sub);
                }
            }

            return new Field_Result(temp_item, strBuilder.toString());
        }
    }

    public static Field_ListRoot root = initGs1Data();

    private static Field_ListRoot initGs1Data()
    {
        Field_ListRoot root = new Field_ListRoot();

        root.addItem("00", "00", "SSCC", "^00(\\d{18})$", 18);
        root.addItem("01", "01", "GTIN", "^01(\\d{14})$", 14);
        root.addItem("02", "02", "CONTENT", "^02(\\d{14})$", 14);
        root.addItem("10", "10", "BATCH_LOT", "^10(" + VALID_CHARS + "{0,20})$");
        root.addItem("11", "11", "PROD_DATE", "^11(\\d{6})$", 6);
        root.addItem("12", "12", "DUE_DATE", "^12(\\d{6})$", 6);
        root.addItem("13", "13", "PACK_DATE", "^13(\\d{6})$", 6);
        root.addItem("15", "15", "BEST_BEFORE", "^15(\\d{6})$", 6);
        root.addItem("16", "16", "SELL_BY", "^16(\\d{6})$", 6);
        root.addItem("17", "17", "USE_BY_EXPIRY", "^17(\\d{6})$", 6);
        root.addItem("20", "20", "VARIANT", "^20(\\d{2})$", 2);
        root.addItem("21", "21", "SERIAL", "^21(" + VALID_CHARS + "{0,20})$");
        root.addItem("22", "22", "CPV", "^22(" + VALID_CHARS + "{0,20})$");

        Field_List group23 = root.addList("23");
        group23.addItem("5", "235", "TPX", "^235(" + VALID_CHARS + "{0,28})$");

        Field_List group24 = root.addList("24");
        group24.addItem("0", "240", "ADDITIONAL_ID", "^240(" + VALID_CHARS + "{0,30})$");
        group24.addItem("1", "241", "CUST_PART_NO", "^241(" + VALID_CHARS + "{0,30})$");
        group24.addItem("2", "242", "MTO_VARIANT", "^242(\\d{0,6})$");
        group24.addItem("3", "243", "PCN", "^243(" + VALID_CHARS + "{0,20})$");

        Field_List group25 = root.addList("25");
        group25.addItem("0", "250", "SECONDARY_SERIAL", "^250(" + VALID_CHARS + "{0,30})$");
        group25.addItem("1", "251", "REF_TO_SOURCE", "^251(" + VALID_CHARS + "{0,30})$");
        group25.addItem("3", "253", "GDTI", "^253(\\d{13})(" + VALID_CHARS + "{0,17})$");
        group25.addItem("4", "254", "GLN_EXTENSION_COMPONENT", "^254(" + VALID_CHARS + "{0,20})$");
        group25.addItem("5", "255", "GCN", "^255(\\d{13})(\\d{0,12})$");

        root.addItem("30", "30", "VAR_COUNT", "^30(\\d{0,8})$");

        Field_List group31 = root.addList("31", 6, true);
        group31.addItem("0", "310", "WEIGHT_NET_KG", "^310([0-5])(\\d{6})$");
        group31.addItem("1", "311", "LENGTH_M", "^311([0-5])(\\d{6})$");
        group31.addItem("2", "312", "WIDTH_M", "^312([0-5])(\\d{6})$");
        group31.addItem("3", "313", "HEIGHT_M", "^313([0-5])(\\d{6})$");
        group31.addItem("4", "314", "AREA_M", "^314([0-5])(\\d{6})$");
        group31.addItem("5", "315", "VOLUME_NET_L", "^315([0-5])(\\d{6})$");
        group31.addItem("6", "316", "VOLUME_NET_M3", "^316([0-5])(\\d{6})$");

        Field_List group32 = root.addList("32", 6, true);
        group32.addItem("0", "320", "WEIGHT_NET_LB", "^320([0-5])(\\d{6})$");
        group32.addItem("1", "321", "LENGTH_IN", "^321([0-5])(\\d{6})$");
        group32.addItem("2", "322", "LENGTH_FT", "^322([0-5])(\\d{6})$");
        group32.addItem("3", "323", "LENGTH_YD", "^323([0-5])(\\d{6})$");
        group32.addItem("4", "324", "WIDTH_IN", "^324([0-5])(\\d{6})$");
        group32.addItem("5", "325", "WIDTH_FT", "^325([0-5])(\\d{6})$");
        group32.addItem("6", "326", "WIDTH_YD", "^326([0-5])(\\d{6})$");
        group32.addItem("7", "327", "HEIGHT_IN", "^327([0-5])(\\d{6})$");
        group32.addItem("8", "328", "HEIGHT_FT", "^328([0-5])(\\d{6})$");
        group32.addItem("9", "329", "HEIGHT_YD", "^329([0-5])(\\d{6})$");

        Field_List group33 = root.addList("33", 6, true);
        group33.addItem("0", "330", "WEIGHT_GROSS_KG", "^330([0-5])(\\d{6})$");
        group33.addItem("1", "331", "LENGTH_LOG_M", "^331([0-5])(\\d{6})$");
        group33.addItem("2", "332", "WIDTH_LOG_M", "^332([0-5])(\\d{6})$");
        group33.addItem("3", "333", "HEIGHT_LOG_M", "^333([0-5])(\\d{6})$");
        group33.addItem("4", "334", "AREA_LOG_M", "^334([0-5])(\\d{6})$");
        group33.addItem("5", "335", "VOLUME_LOG_L", "^335([0-5])(\\d{6})$");
        group33.addItem("6", "336", "VOLUME_LOG_M3", "^336([0-5])(\\d{6})$");
        group33.addItem("7", "337", "KG_PER_M2", "^337([0-5])(\\d{6})$");

        Field_List group34 = root.addList("34", 6, true);
        group34.addItem("0", "340", "WEIGHT_GROSS_LB", "^340([0-5])(\\d{6})$");
        group34.addItem("1", "341", "LENGTH_LOG_IN", "^341([0-5])(\\d{6})$");
        group34.addItem("2", "342", "LENGTH_LOG_FT", "^342([0-5])(\\d{6})$");
        group34.addItem("3", "343", "LENGTH_LOG_YD", "^343([0-5])(\\d{6})$");
        group34.addItem("4", "344", "WIDTH_LOG_IN", "^344([0-5])(\\d{6})$");
        group34.addItem("5", "345", "WIDTH_LOG_FT", "^345([0-5])(\\d{6})$");
        group34.addItem("6", "346", "WIDTH_LOG_YD", "^346([0-5])(\\d{6})$");
        group34.addItem("7", "347", "HEIGHT_LOG_IN", "^347([0-5])(\\d{6})$");
        group34.addItem("8", "348", "HEIGHT_LOG_FT", "^348([0-5])(\\d{6})$");
        group34.addItem("9", "349", "HEIGHT_LOG_YD", "^349([0-5])(\\d{6})$");

        Field_List group35 = root.addList("35", 6, true);
        group35.addItem("0", "350", "AREA_IN", "^350([0-5])(\\d{6})$");
        group35.addItem("1", "351", "AREA_FT", "^351([0-5])(\\d{6})$");
        group35.addItem("2", "352", "AREA_YD", "^352([0-5])(\\d{6})$");
        group35.addItem("3", "353", "AREA_LOG_IN", "^353([0-5])(\\d{6})$");
        group35.addItem("4", "354", "AREA_LOG_FT", "^354([0-5])(\\d{6})$");
        group35.addItem("5", "355", "AREA_LOG_YD", "^355([0-5])(\\d{6})$");
        group35.addItem("6", "356", "WEIGHT_NET_T", "^356([0-5])(\\d{6})$");
        group35.addItem("7", "357", "VOLUME_NET_OZ", "^357([0-5])(\\d{6})$");

        Field_List group36 = root.addList("36", 6, true);
        group36.addItem("0", "360", "VOLUME_NET_QT", "^360([0-5])(\\d{6})$");
        group36.addItem("1", "361", "VOLUME_NET_GAL", "^361([0-5])(\\d{6})$");
        group36.addItem("2", "362", "VOLUME_LOG_QT", "^362([0-5])(\\d{6})$");
        group36.addItem("3", "363", "VOLUME_LOG_GAL", "^363([0-5])(\\d{6})$");
        group36.addItem("4", "364", "VOLUME_IN3", "^364([0-5])(\\d{6})$");
        group36.addItem("5", "365", "VOLUME_FT3", "^365([0-5])(\\d{6})$");
        group36.addItem("6", "366", "VOLUME_YD3", "^366([0-5])(\\d{6})$");
        group36.addItem("7", "367", "VOLUME_LOG_IN3", "^367([0-5])(\\d{6})$");
        group36.addItem("8", "368", "VOLUME_LOG_FT3", "^368([0-5])(\\d{6})$");
        group36.addItem("9", "369", "VOLUME_LOG_YD3", "^369([0-5])(\\d{6})$");

        root.addItem("37", "37", "COUNT", "^37(\\d{0,8})$");

        Field_List group39 = root.addList("39", 0, true);
        group39.addItem("0", "390", "AMOUNT_LOCAL", "^390([0-9])(\\d{0,15})$");
        group39.addItem("1", "391", "AMOUNT_ISO4217", "^391([0-9])(\\d{3})(\\d{0,15})$");
        group39.addItem("2", "392", "PRICE_LOCAL", "^392([0-9])(\\d{0,15})$");
        group39.addItem("3", "393", "PRICE_ISO421", "^393([0-9])(\\d{3})(\\d{0,15})$");
        group39.addItem("4", "394", "PRCNT_OFF", "^394([0-3])(\\d{4})$");
        group39.addItem("5", "395", "PRICE_UOM", "^395([0-9])(\\d{0,6})$");

        Field_List group40 = root.addList("40");
        group40.addItem("0", "400", "ORDER_NUMBER", "^400(" + VALID_CHARS + "{0,30})$");
        group40.addItem("1", "401", "GINC", "^401(" + VALID_CHARS + "{0,30})$");
        group40.addItem("2", "402", "GSIN", "^402(\\d{17})$");
        group40.addItem("3", "403", "ROUTE", "^403(" + VALID_CHARS + "{0,30})$");

        Field_List group41 = root.addList("41", 13, false);
        group41.addItem("0", "410", "SHIP_TO_LOC", "^410(\\d{13})$");
        group41.addItem("1", "411", "BILL_TO", "^411(\\d{13})$");
        group41.addItem("2", "412", "PURCHASE_FROM", "^412(\\d{13})$");
        group41.addItem("3", "413", "SHIP_FOR_LOC", "^413(\\d{13})$");
        group41.addItem("4", "414", "LOC_NO", "^414(\\d{13})$");
        group41.addItem("5", "415", "PAY_TO", "^415(\\d{13})$");
        group41.addItem("6", "416", "PROD_SERV_LOC", "^416(\\d{13})$");
        group41.addItem("7", "417", "PARTY", "^417(\\d{13})$");

        Field_List group42 = root.addList("42");
        group42.addItem("0", "420", "SHIP_TO_POST", "^420(" + VALID_CHARS + "{0,20})$");
        group42.addItem("1", "421", "SHIP_TO_POST", "^421(\\d{3})(" + VALID_CHARS + "{0,9})$");
        group42.addItem("2", "422", "ORIGIN", "^422(\\d{3})$");
        group42.addItem("3", "423", "COUNTRY_INITIAL_PROCESS", "^423(\\d{3})(\\d{0,12})$");
        group42.addItem("4", "424", "COUNTRY_PROCESS", "^424(\\d{3})$");
        group42.addItem("5", "425", "COUNTRY_DISASSEMBLY", "^425(\\d{3})(\\d{0,12})$");
        group42.addItem("6", "426", "COUNTRY_FULL_PROCESS", "^426(\\d{3})$");
        group42.addItem("7", "427", "ORIGIN_SUBDIVISION", "^427(" + VALID_CHARS + "{0,3})$");

        Field_List group43 = root.addList("43");
        Field_List group430 = group43.addList("0");
        group430.addItem("0", "4300", "SHIP_TO_COMP", "^4300(" + VALID_CHARS + "{0,35})$");
        group430.addItem("1", "4301", "SHIP_TO_NAME", "^4301(" + VALID_CHARS + "{0,35})$");
        group430.addItem("2", "4302", "SHIP_TO_ADD1", "^4302(" + VALID_CHARS + "{0,70})$");
        group430.addItem("3", "4303", "SHIP_TO_ADD2", "^4303(" + VALID_CHARS + "{0,70})$");
        group430.addItem("4", "4304", "SHIP_TO_SUB", "^4304(" + VALID_CHARS + "{0,70})$");
        group430.addItem("5", "4305", "SHIP_TO_LOC", "^4305(" + VALID_CHARS + "{0,70})$");
        group430.addItem("6", "4306", "SHIP_TO_REG", "^4306(" + VALID_CHARS + "{0,70})$");
        group430.addItem("7", "4307", "SHIP_TO_COUNTRY", "^4307([A-Z]{2})$");
        group430.addItem("8", "4308", "SHIP_TO_PHONE", "^4308(" + VALID_CHARS + "{0,30})$");
        group430.addItem("9", "4309", "SHIP_TO_GEO", "^4309(\\d{20})$");

        Field_List group431 = group43.addList("1");
        group431.addItem("0", "4310", "RTN_TO_COMP", "^4310(" + VALID_CHARS + "{0,35})$");
        group431.addItem("1", "4311", "RTN_TO_NAME", "^4311(" + VALID_CHARS + "{0,35})$");
        group431.addItem("2", "4312", "RTN_TO_ADD1", "^4312(" + VALID_CHARS + "{0,70})$");
        group431.addItem("3", "4313", "RTN_TO_ADD2", "^4313(" + VALID_CHARS + "{0,70})$");
        group431.addItem("4", "4314", "RTN_TO_SUB", "^4314(" + VALID_CHARS + "{0,70})$");
        group431.addItem("5", "4315", "RTN_TO_LOC", "^4315(" + VALID_CHARS + "{0,70})$");
        group431.addItem("6", "4316", "RTN_TO_REG", "^4316(" + VALID_CHARS + "{0,70})$");
        group431.addItem("7", "4317", "RTN_TO_COUNTRY", "^4317([A-Z]{2})$");
        group431.addItem("8", "4318", "RTN_TO_POST", "^4318(" + VALID_CHARS + "{0,20})$");
        group431.addItem("9", "4319", "RTN_TO_PHONE", "^4319(" + VALID_CHARS + "{0,30})$");

        Field_List group432 = group43.addList("2");
        group432.addItem("0", "4320", "SRV_DESCRIPTION", "^4320(" + VALID_CHARS + "{0,35})$");
        group432.addItem("1", "4321", "DANGEROUS_GOODS", "^4321([01])$");
        group432.addItem("2", "4322", "AUTH_TO_LEAVE", "^4322([01])$");
        group432.addItem("3", "4323", "SIG_REQUIRED", "^4323([01])$");
        group432.addItem("4", "4324", "NBEF_DEL_DT", "^4324(\\d{10})$");
        group432.addItem("5", "4325", "NAFT_DEL_DT", "^4325(\\d{10})$");
        group432.addItem("6", "4326", "REL_DATE", "^4326(\\d{6})$");

        Field_List group70 = root.addList("70");
        Field_List group700 = group70.addList("0");
        group700.addItem("1", "7001", "NSN", "^7001(\\d{13})$");
        group700.addItem("2", "7002", "MEAT_CUT", "^7002(" + VALID_CHARS + "{0,30})$");
        group700.addItem("3", "7003", "EXPIRY_TIME", "^7003(\\d{10})$");
        group700.addItem("4", "7004", "ACTIVE_POTENCY", "^7004(\\d{0,4})$");
        group700.addItem("5", "7005", "CATCH_AREA", "^7005(" + VALID_CHARS + "{0,12})$");
        group700.addItem("6", "7006", "FIRST_FREEZE_DATE", "^7006(\\d{6})$");
        group700.addItem("7", "7007", "HARVEST_DATE", "^7007(\\d{6,12})$");
        group700.addItem("8", "7008", "AQUATIC_SPECIES", "^7008(" + VALID_CHARS + "{0,3})$");
        group700.addItem("9", "7009", "FISHING_GEAR_TYPE", "^7009(" + VALID_CHARS + "{0,10})$");

        Field_List group701 = group70.addList("1");
        group701.addItem("0", "7010", "PROD_METHOD", "^7010(" + VALID_CHARS + "{0,2})$");
        group701.addItem("1", "7011", "TEST_BY_DATE", "^7011(\\d{6})(\\d{0,4})$");

        Field_List group702 = group70.addList("2");
        group702.addItem("0", "7020", "REFURB_LOT", "^7020(" + VALID_CHARS + "{0,20})$");
        group702.addItem("1", "7021", "FUNC_STAT", "^7021(" + VALID_CHARS + "{0,20})$");
        group702.addItem("2", "7022", "REV_STAT", "^7022(" + VALID_CHARS + "{0,20})$");
        group702.addItem("3", "7023", "GIAI_ASSEMBLY", "^7023(" + VALID_CHARS + "{0,30})$");

        Field_List group703 = group70.addList("3");
        group703.addItem("0", "7030", "PROCESSOR_0", "^7030(\\d{3})(" + VALID_CHARS + "{0,27})$");
        group703.addItem("1", "7031", "PROCESSOR_1", "^7031(\\d{3})(" + VALID_CHARS + "{0,27})$");
        group703.addItem("2", "7032", "PROCESSOR_2", "^7032(\\d{3})(" + VALID_CHARS + "{0,27})$");
        group703.addItem("3", "7033", "PROCESSOR_3", "^7033(\\d{3})(" + VALID_CHARS + "{0,27})$");
        group703.addItem("4", "7034", "PROCESSOR_4", "^7034(\\d{3})(" + VALID_CHARS + "{0,27})$");
        group703.addItem("5", "7035", "PROCESSOR_5", "^7035(\\d{3})(" + VALID_CHARS + "{0,27})$");
        group703.addItem("6", "7036", "PROCESSOR_6", "^7036(\\d{3})(" + VALID_CHARS + "{0,27})$");
        group703.addItem("7", "7037", "PROCESSOR_7", "^7037(\\d{3})(" + VALID_CHARS + "{0,27})$");
        group703.addItem("8", "7038", "PROCESSOR_8", "^7038(\\d{3})(" + VALID_CHARS + "{0,27})$");
        group703.addItem("9", "7039", "PROCESSOR_9", "^7039(\\d{3})(" + VALID_CHARS + "{0,27})$");

        Field_List group704 = group70.addList("4");
        group704.addItem("0", "7040", "UIC_EXT", "^7040(\\d[\\x21-\\x22\\x25-\\x2F\\x30-\\x39\\x41-\\x5A\\x5F\\x61-\\x7A]{3})$");

        Field_List group71 = root.addList("71");
        group71.addItem("0", "710", "NHRN_PZN", "^710(" + VALID_CHARS + "{0,20})$");
        group71.addItem("1", "711", "NHRN_CIP", "^711(" + VALID_CHARS + "{0,20})$");
        group71.addItem("2", "712", "NHRN_CN", "^712(" + VALID_CHARS + "{0,20})$");
        group71.addItem("3", "713", "NHRN_DRN", "^713(" + VALID_CHARS + "{0,20})$");
        group71.addItem("4", "714", "NHRN_AIM", "^714(" + VALID_CHARS + "{0,20})$");
        group71.addItem("5", "715", "NHRN_NDC", "^714(" + VALID_CHARS + "{0,20})$");

        Field_List group72 = root.addList("72");
        Field_List group723 = group72.addList("3");
        group723.addItem("0", "7230", "CERT_1", "^7230(" + VALID_CHARS + "{2,30})$");
        group723.addItem("1", "7231", "CERT_2", "^7231(" + VALID_CHARS + "{2,30})$");
        group723.addItem("2", "7232", "CERT_3", "^7232(" + VALID_CHARS + "{2,30})$");
        group723.addItem("3", "7233", "CERT_4", "^7233(" + VALID_CHARS + "{2,30})$");
        group723.addItem("4", "7234", "CERT_5", "^7234(" + VALID_CHARS + "{2,30})$");
        group723.addItem("5", "7235", "CERT_6", "^7235(" + VALID_CHARS + "{2,30})$");
        group723.addItem("6", "7236", "CERT_7", "^7236(" + VALID_CHARS + "{2,30})$");
        group723.addItem("7", "7237", "CERT_8", "^7237(" + VALID_CHARS + "{2,30})$");
        group723.addItem("8", "7238", "CERT_9", "^7238(" + VALID_CHARS + "{2,30})$");
        group723.addItem("9", "7239", "CERT_10", "^7239(" + VALID_CHARS + "{2,30})$");

        Field_List group724 = group72.addList("4");
        group724.addItem("0", "7240", "PROTOCOL", "^7240 ([\\x21-\\x22\\x25-\\x2F\\x30-\\x39\\x41-\\x5A\\x5F\\x61-\\x7A]{0,20})$");

        Field_List group80 = root.addList("80");
        Field_List group800 = group80.addList("0");
        group800.addItem("1", "8001", "DIMENSIONS", "^8001(\\d{14})$");
        group800.addItem("2", "8002", "CMT_NO", "^8002(" + VALID_CHARS + "{0,20})$");
        group800.addItem("3", "8003", "GRAI", "^8003(\\d{14})(" + VALID_CHARS + "{0,16})$");
        group800.addItem("4", "8004", "GIAI", "^8004(" + VALID_CHARS + "{0,30})$");
        group800.addItem("5", "8005", "PRICE_PER_UNIT", "^8005(\\d{6})$");
        group800.addItem("6", "8006", "ITIP", "^8006(\\d{14})(\\d{2})(\\d{2})$");
        group800.addItem("7", "8007", "IBAN", "^8007(" + VALID_CHARS + "{0,34})$");
        group800.addItem("8", "8008", "PROD_TIME", "^8008(\\d{8})(\\d{0,4})$");
        group800.addItem("9", "8009", "OPTSEN", "^8009(" + VALID_CHARS + "{0,50})$");

        Field_List group801 = group80.addList("1");
        group801.addItem("0", "8010", "CPID", "^8010([\\x23\\x2D\\x2F\\x30-\\x39\\x41-\\x5A]{5,30})$");
        group801.addItem("1", "8011", "CPID_SERIAL", "^8011(\\d{0,12})$");
        group801.addItem("2", "8012", "VERSION", "^8012(" + VALID_CHARS + "{0,20})$");
        group801.addItem("3", "8013", "GMN", "^8013(" + VALID_CHARS + "{0,25})$");
        group801.addItem("7", "8017", "GSRN_PROVIDER", "^8017(\\d{18})$");
        group801.addItem("8", "8018", "GSRN_RECIPIENT", "^8018(\\d{18})$");
        group801.addItem("9", "8019", "SRIN", "^8019(\\d{0,10})$");

        Field_List group802 = group80.addList("2");
        group802.addItem("0", "8020", "REF_NO", "^8020(" + VALID_CHARS + "{0,25})$");
        group802.addItem("6", "8026", "ITIP_CONTENT", "^8026(\\d{14})(\\d{2})(\\d{2})$");


        Field_List group81 = root.addList("81");
        Field_List group811 = group81.addList("1");
        group811.addItem("0", "8110", "CCI_NA", "^8110(" + VALID_CHARS + "{0,70})$");
        group811.addItem("1", "8111", "POINTS", "^8111(\\d{4})$");
        group811.addItem("2", "8112", "CCI_PL_NA", "^8112(" + VALID_CHARS + "{0,70})$");

        Field_List group82 = root.addList("82");
        Field_List group820 = group82.addList("0");
        group820.addItem("0", "8200", "PRODUCT_URL", "^8200(" + VALID_CHARS + "{0,70})$");

        root.addItem("90", "90", "INFO_BTW_PART", "^90(" + VALID_CHARS + "{0,30})$");
        root.addItem("91", "91", "INTERNAL_1", "^91(" + VALID_CHARS + "{0,90})$");
        root.addItem("92", "92", "INTERNAL_2", "^92(" + VALID_CHARS + "{0,90})$");
        root.addItem("93", "93", "INTERNAL_3", "^93(" + VALID_CHARS + "{0,90})$");
        root.addItem("94", "94", "INTERNAL_4", "^94(" + VALID_CHARS + "{0,90})$");
        root.addItem("95", "95", "INTERNAL_5", "^95(" + VALID_CHARS + "{0,90})$");
        root.addItem("96", "96", "INTERNAL_6", "^96(" + VALID_CHARS + "{0,90})$");
        root.addItem("97", "97", "INTERNAL_7", "^97(" + VALID_CHARS + "{0,90})$");
        root.addItem("98", "98", "INTERNAL_8", "^98(" + VALID_CHARS + "{0,90})$");
        root.addItem("99", "99", "INTERNAL_9", "^99(" + VALID_CHARS + "{0,90})$");

        return root;
    }

}
