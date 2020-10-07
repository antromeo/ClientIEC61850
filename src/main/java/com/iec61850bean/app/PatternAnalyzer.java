package main.java.com.iec61850bean.app;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternAnalyzer {
    private static PatternAnalyzer istance=null; // riferimento all' istanza
    private static final Pattern TAG_REGEX_IEDNAME = Pattern.compile("<IED name=\"(.+?)\">", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LDNAME = Pattern.compile("<LDevice inst=\"(.+?)\">", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LD = Pattern.compile("<LDevice(.+?)</LDevice>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LN0 = Pattern.compile("<LN0 lnClass(.+?)</LN0>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LN = Pattern.compile("<LN lnClass(.+?)</LN>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_DO = Pattern.compile("<DO(.+?)/>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_DOTYPES = Pattern.compile("<DOType(.+?)</DOType>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_DA = Pattern.compile("<DA(.+?)/>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_DATYPES = Pattern.compile("<DAType(.+?)</DAType>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_DATATYPETEMPLATES = Pattern
            .compile("<DataTypeTemplates>(.+?)</DataTypeTemplates>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LNTYPE = Pattern.compile("<LNodeType(.+?)</LNodeType>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_ENUMTYPE = Pattern.compile("<EnumType(.+?)</EnumType>", Pattern.DOTALL);

    private PatternAnalyzer() {} // costruttore

    public static PatternAnalyzer getIstance() {
        if(istance==null)
            istance = new PatternAnalyzer();
        return istance;
    }

    /*QUESTE FUNZIONI VENGONO UTILIZZATE DAL CLIENT 61850 PER SELEZIONARE I PEZZI SCL DI INTERESSE*/
    public String getIED(final String str) {
        final Matcher matcher = TAG_REGEX_IEDNAME.matcher(str);
        String iedName = "";
        while (matcher.find()) {
            iedName = matcher.group(1);
        }
        return iedName;
    }

    public String getLogicalDeviceName(final String str) {
        final Matcher matcher = TAG_REGEX_LDNAME.matcher(str);
        String ldName = "";
        while (matcher.find()) {
            ldName = matcher.group(1);
        }
        return ldName;
    }

    public ArrayList<String> getLogicalDevice(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_LD.matcher(str);
        while (matcher.find()) {
            tagValues.add("<LDevice" + matcher.group(1) + "</LDevice>");
        }
        return tagValues;
    }

    public String getLogicalNodeZero(final String str) {
        final Matcher matcher = TAG_REGEX_LN0.matcher(str);
        String lnodezero = "";
        while (matcher.find()) {
            lnodezero = "<LN0 lnClass" + matcher.group(1) + "</LN0>";
        }
        return lnodezero;
    }

    public ArrayList<String> getLogicalNodes(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_LN.matcher(str);
        while (matcher.find()) {
            tagValues.add("<LN lnClass" + matcher.group(1) + "</LN>");
        }
        return tagValues;
    }

    public String getDTTemplates(final String str) {
        final Matcher matcher = TAG_REGEX_DATATYPETEMPLATES.matcher(str);
        String dataTypeTemplates = "";
        while (matcher.find()) {
            dataTypeTemplates = "<DataTypeTemplates>" + matcher.group(1) + "</DataTypeTemplates>";
        }
        return dataTypeTemplates;
    }

    public ArrayList<String> getLNTypeValues(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_LNTYPE.matcher(str);
        while (matcher.find()) {
            tagValues.add("<LNodeType" + matcher.group(1) + "</LNodeType>");
        }
        return tagValues;
    }

    public ArrayList<String> getDO(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DO.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DO" + matcher.group(1) + "/>");
        }
        return tagValues;
    }

    public ArrayList<String> getDOTypes(String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DOTYPES.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DOType" + matcher.group(1) + "</DOType>");
        }
        return tagValues;
    }

    public String convertSelfClosedTag(String str){
        Pattern patt = Pattern.compile("(<DOType[^>]*)(\\s*/>)");
        Matcher mattcher = patt.matcher(str);
        while (mattcher.find()){
            str = mattcher.replaceAll("$1></DOType>");
        }
        return str;
    }

    public ArrayList<String> getDA(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DA.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DA" + matcher.group(1) + "/>");
        }
        return tagValues;
    }



    public ArrayList<String> getDATypes(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DATYPES.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DAType" + matcher.group(1) + "</DAType>");
        }
        return tagValues;
    }

    public ArrayList<String> getEnumTypes(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_ENUMTYPE.matcher(str);
        while (matcher.find()) {
            tagValues.add("<EnumType" + matcher.group(1) + "</EnumType>");
        }
        return tagValues;
    }



}
