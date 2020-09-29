package main.java.com.iec61850bean.app;

import com.beanit.iec61850bean.*;
import com.beanit.iec61850bean.internal.cli.*;

import org.json.*;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.regex.Matcher;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class Client61850 {
    private static final String CONVERT_JSON = "j";
    private static final String CONVERT_JSON_DESCRIPTION = "convert SCL to JSON";
    private static final String PRINT_MODEL_KEY = "m";
    private static final String PRINT_MODEL_KEY_DESCRIPTION = "print model";
    private static final String GET_DATA_VALUES_KEY = "g";
    private static final String GET_DATA_VALUES_KEY_DESCRIPTION = "send GetDataValues request";
    private static final String READ_ALL_DATA_KEY = "ga";
    private static final String READ_ALL_DATA_KEY_DESCRIPTION = "update all data in the model";
    private static final String CREATE_DATA_SET_KEY = "cds";
    private static final String CREATE_DATA_SET_KEY_DESCRIPTION = "create data set";
    private static final String DELETE_DATA_SET_KEY = "dds";
    private static final String DELETE_DATA_SET_KEY_DESCRIPTION = "delete data set";
    private static final String REPORTING_KEY = "r";
    private static final String REPORTING_KEY_DESCRIPTION = "configure reporting";

    private static final StringCliParameter hostParam =
            new CliParameterBuilder("-h")
                    .setDescription("The IP/domain address of the server you want to access.")
                    .setMandatory()
                    .buildStringParameter("host");
    private static final IntCliParameter portParam =
            new CliParameterBuilder("-p")
                    .setDescription("The port to connect to.")
                    .buildIntParameter("port", 102);
    private static final StringCliParameter modelFileParam =
            new CliParameterBuilder("-m")
                    .setDescription(
                            "The file name of the SCL file to read the model from. If this parameter is omitted the model will be read from the server device after connection.")
                    .buildStringParameter("model-file");
    private static final ActionProcessor actionProcessor = new ActionProcessor(new ActionExecutor());
    private static volatile ClientAssociation association;
    private static ServerModel serverModel;

    public static ArrayList<String> topics = new ArrayList();
    public static String logicalDevice;
    public static String pubTopic;
    public static String lnType0;
    public static String lnType;

    public static void main(String[] args) {
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        List<CliParameter> cliParameters = new ArrayList<>();
        cliParameters.add(hostParam);
        cliParameters.add(portParam);
        cliParameters.add(modelFileParam);

        CliParser cliParser =
                new CliParser(
                        "openiec61850-console-client", "A client application to access IEC 61850 MMS servers.");
        cliParser.addParameters(cliParameters);

        try {
            cliParser.parseArguments(args);
        } catch (CliParseException e1) {
            System.err.println("Error parsing command line parameters: " + e1.getMessage());
            System.out.println(cliParser.getUsageString());
            System.exit(1);
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(hostParam.getValue());
        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + hostParam.getValue());
            return;
        }

        ClientSap clientSap = new ClientSap();

        try {
            association = clientSap.associate(address, portParam.getValue(), null, new EventListener());


        } catch (IOException e) {
            System.out.println("Unable to connect to remote host.");
            return;
        }

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread() {
                            @Override
                            public void run() {
                                association.close();
                            }
                        });

        System.out.println("successfully connected");

        if (modelFileParam.isSelected()) {
            System.out.println("reading model from file...");

            try {
                serverModel = SclParser.parse(modelFileParam.getValue()).get(0);
            } catch (SclParseException e1) {
                System.out.println("Error parsing SCL file: " + e1.getMessage());
                return;
            }

            JSONObject jsonSCL = XML.toJSONObject(modelFileParam.getValue());
            System.out.println("JsonSCL" + jsonSCL);

            association.setServerModel(serverModel);

            System.out.println("successfully read model");

        } else {
            System.out.println("retrieving model...");


            try {

                serverModel = association.retrieveModel();

            } catch (ServiceError e) {
                System.out.println("Service error: " + e.getMessage());
                return;
            } catch (IOException e) {
                System.out.println("Fatal error: " + e.getMessage());
                return;
            }

            System.out.println("successfully read model");
        }


        actionProcessor.addAction(new Action(PRINT_MODEL_KEY, PRINT_MODEL_KEY_DESCRIPTION));

        actionProcessor.addAction(new Action(CONVERT_JSON, CONVERT_JSON_DESCRIPTION));

        actionProcessor.addAction(new Action(GET_DATA_VALUES_KEY, GET_DATA_VALUES_KEY_DESCRIPTION));
        actionProcessor.addAction(new Action(READ_ALL_DATA_KEY, READ_ALL_DATA_KEY_DESCRIPTION));
        actionProcessor.addAction(new Action(CREATE_DATA_SET_KEY, CREATE_DATA_SET_KEY_DESCRIPTION));
        actionProcessor.addAction(new Action(DELETE_DATA_SET_KEY, DELETE_DATA_SET_KEY_DESCRIPTION));
        actionProcessor.addAction(new Action(REPORTING_KEY, REPORTING_KEY_DESCRIPTION));

        actionProcessor.start();
    }

    private static final Pattern TAG_REGEX_IEDNAME = Pattern.compile("<IED name=\"(.+?)\">", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LDNAME = Pattern.compile("<LDevice inst=\"(.+?)\">", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LD = Pattern.compile("<LDevice(.+?)</LDevice>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LN0 = Pattern.compile("<LN0 lnClass(.+?)</LN0>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_LN = Pattern.compile("<LN lnClass(.+?)</LN>", Pattern.DOTALL);

    private static final Pattern TAG_REGEX_DO = Pattern.compile("<DO(.+?)/>", Pattern.DOTALL);
    private static final Pattern TAG_REGEX_DOTYPES = Pattern.compile("<DOType(.+?)/DOType>", Pattern.DOTALL);

    private static final Pattern TAG_REGEX_DA = Pattern.compile("<DA(.+?)/>", Pattern.DOTALL);

    private static final Pattern TAG_REGEX_DATATYPETEMPLATES = Pattern
            .compile("<DataTypeTemplates>(.+?)</DataTypeTemplates>", Pattern.DOTALL);

    private static final Pattern TAG_REGEX_LNTYPE = Pattern.compile("<LNodeType(.+?)</LNodeType>", Pattern.DOTALL);

    private static final ArrayList<String> topicDisponibili = new ArrayList<String>();

    private static String getIED(final String str) {
        final Matcher matcher = TAG_REGEX_IEDNAME.matcher(str);
        String iedName = "";
        while (matcher.find()) {
            iedName = matcher.group(1);
        }
        return iedName;
    }

    private static String getLogicalDeviceName(final String str) {
        final Matcher matcher = TAG_REGEX_LDNAME.matcher(str);
        String ldName = "";
        while (matcher.find()) {
            ldName = matcher.group(1);
        }
        return ldName;
    }

    private static ArrayList<String> getLogicalDevice(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_LD.matcher(str);
        while (matcher.find()) {
            tagValues.add("<LDevice" + matcher.group(1) + "</LDevice>");
        }
        return tagValues;
    }

    private static String getLogicalNodeZero(final String str) {
        final Matcher matcher = TAG_REGEX_LN0.matcher(str);
        String lnodezero = "";
        while (matcher.find()) {
            lnodezero = "<LN0 lnClass" + matcher.group(1) + "</LN0>";
        }
        return lnodezero;
    }

    private static ArrayList<String> getLogicalNodes(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_LN.matcher(str);
        while (matcher.find()) {
            tagValues.add("<LN lnClass" + matcher.group(1) + "</LN>");
        }
        return tagValues;
    }

    private static String getDTTemplates(final String str) {
        final Matcher matcher = TAG_REGEX_DATATYPETEMPLATES.matcher(str);
        String dataTypeTemplates = "";
        while (matcher.find()) {
            dataTypeTemplates = "<DataTypeTemplates>" + matcher.group(1) + "</DataTypeTemplates>";
        }
        return dataTypeTemplates;
    }

    private static ArrayList<String> getLNTypeValues(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_LNTYPE.matcher(str);
        while (matcher.find()) {
            tagValues.add("<LNodeType" + matcher.group(1) + "</LNodeType>");
        }
        return tagValues;
    }

    private static ArrayList<String> getDO(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DO.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DO" + matcher.group(1) + "/>");
        }
        return tagValues;
    }

    private static ArrayList<String> getDOTypes(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DOTYPES.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DOType" + matcher.group(1) + "/DOType>");
        }
        return tagValues;
    }

    private static ArrayList<String> getDA(final String str) {
        final ArrayList<String> tagValues = new ArrayList<String>();
        final Matcher matcher = TAG_REGEX_DA.matcher(str);
        while (matcher.find()) {
            tagValues.add("<DA" + matcher.group(1) + "/>");
        }
        return tagValues;
    }

    private static class EventListener implements ClientEventListener {

        @Override
        public void newReport(Report report) {
            System.out.println("\n----------------");
            System.out.println("Received report: ");
            System.err.println(report);
            System.out.println("------------------");
        }

        @Override
        public void associationClosed(IOException e) {
            System.out.print("Received connection closed signal. Reason: ");
            if (!e.getMessage().isEmpty()) {
                System.out.println(e.getMessage());
            } else {
                System.out.println("unknown");
            }
            actionProcessor.close();
        }
    }

    private static class ActionExecutor implements ActionListener {

        @Override
        public void actionCalled(String actionKey) throws ActionException {
            try {
                switch (actionKey) {
                    case PRINT_MODEL_KEY:

                        System.out.println(serverModel);

                        break;
                    case CONVERT_JSON:
                        /*DEFINIZIONE CLIENT MQTT*/
                        int qos = 2;
                        String broker = "tcp://localhost:1883";
                        String clientId = "clientIEC61850";
                        MemoryPersistence persistence = new MemoryPersistence();
                        MqttClient sampleClient = new MqttClient(broker, clientId, persistence);
                        MqttConnectOptions connOpts = new MqttConnectOptions();
                        connOpts.setCleanSession(false);
                        sampleClient.connect(connOpts);
                        /*FINE DEFINIZIONE CLIENT MQTT*/

                        /*LETTURA DA FILE SCL*/
                        String line = null;
                        String str = "";
                        //String link = "sample-model.icd";
                        System.out.println(modelFileParam.getName());
                        String link = modelFileParam.getName();
                        BufferedReader br = new BufferedReader(new FileReader(link));
                        while ((line = br.readLine()) != null) {
                            str += line;
                        }
                        /*FINE LETTURA FILE SCL*/

                        /*OTTENGO IL NOME DELL'IED*/
                        String iedName = getIED(str);
                        pubTopic = iedName + "/";

                        //Ottengo la lista dei logical device presenti nel file
                        ArrayList<String> lDevices = getLogicalDevice(str);

                        //Ottengo la parte relativa al data type templates e la salvo in una stringa
                        String dataTypeTemplates = getDTTemplates(str);

                        for (int i = 0; i < lDevices.size(); i++)
                        { //finisce alla riga 761 - è questa tutta la parte di traduzione per ogni logical device

                            logicalDevice = lDevices.get(i); //estraggo la parte scl relativa al logical device
                            String logicalDeviceName = getLogicalDeviceName(logicalDevice); //ottengo il nome
                            pubTopic = pubTopic + logicalDeviceName + "/"; //e il nome del logical device lo aggiungo al topic

                            String ln0 = getLogicalNodeZero(logicalDevice); //qui salvo la parte relativa al nodo logico zero
                            ArrayList<String> lNodes = getLogicalNodes(logicalDevice); //qui salvo la parte scl relativa a tutti gli altri nodi logici

                            JSONObject jsondataLN0 = XML.toJSONObject(ln0); //la parte scl relativa al nodo logico zero la converto in un oggetto json
                            lnType0 = jsondataLN0.getJSONObject("LN0").getString("lnType"); //dell'oggetto json ottenuto, ottengo il valore di lnType
                            pubTopic = pubTopic + lnType0; //e lo aggiungo al topic

                            ArrayList<String> LNTypes = getLNTypeValues(str);

                            /*NODO LOGICO ZERO*/
                            for (int q = 0; q < LNTypes.size(); q++)
                            {
                                String LNodeType = LNTypes.get(q);
                                JSONObject jsondataLNType = XML.toJSONObject(LNodeType);
                                String id = jsondataLNType.getJSONObject("LNodeType").getString("id");
                                if (id.equals(lnType0)) {
                                    System.out.println(pubTopic);
                                    System.out.println("JSON Payload: " + jsondataLNType);
                                    MqttMessage messageLNType = new MqttMessage(jsondataLNType.toString().getBytes());
                                    messageLNType.setQos(qos);
                                    messageLNType.setRetained(true);
                                    sampleClient.publish(pubTopic, messageLNType);
                                    topicDisponibili.add(pubTopic);
                                    /*LA TIPIZZAZIONE DI QUESTO NODO LOGICO VERRA' PUBBLICATA SU QUESTO TOPIC*/

                                    /*QUINDI SI PASSA AI DATA OBJECT*/
                                    ArrayList<String> dataObjects = getDO(LNodeType);
                                    for (int j = 0; j < dataObjects.size(); j++) {
                                        //System.out.println(dataObjects.get(j)+"\n");
                                        String DataObject = dataObjects.get(j);
                                        JSONObject jsondataDO = XML.toJSONObject(DataObject);
                                        //System.out.println("JSON Payload DO: "+jsondataDO);

                                        String nameDO = jsondataDO.getJSONObject("DO").getString("name");
                                        String typeDO = jsondataDO.getJSONObject("DO").getString("type");

                                        pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType0 + "." + nameDO;


                                        System.out.println("pubTopic: " + pubTopic);
                                        System.out.println("json payload data object: " + jsondataDO);
                                        MqttMessage messageDO = new MqttMessage(jsondataDO.toString().getBytes());
                                        messageDO.setQos(qos);
                                        messageDO.setRetained(true);
                                        sampleClient.publish(pubTopic, messageDO);
                                        topicDisponibili.add(pubTopic);

                                        //System.out.println(nameDO);
                                        //System.out.println(typeDO+"\n");

                                        /*A questo punto si passa al data type templates e si ottengono i data object type*/

                                        /*Data object types con i rispettivi data attribute*/
                                        ArrayList<String> DOTypes = getDOTypes(dataTypeTemplates);

                                        for (int y = 0; y < DOTypes.size(); y++) {
                                            //System.out.println("i: "+DOTypes.get(y)+"\n");

                                            String DataObjectTypes = DOTypes.get(y);

                                            JSONObject jsondataDOTypes = XML.toJSONObject(DataObjectTypes);

                                            if (jsondataDOTypes.get("DOType") instanceof JSONArray) {

                                                JSONArray recs = jsondataDOTypes.getJSONArray("DOType");

                                                for (int z = 0; z < recs.length(); z++) {
                                                    JSONObject rec = recs.getJSONObject(z);

                                                    String jsonString = "{\"DOType\":" + rec.toString() + "}";
                                                    JSONObject jsonObject = new JSONObject(jsonString);

                                                    String idDO = jsonObject.getJSONObject("DOType").getString("id");

                                                    if (idDO.equals(typeDO)) {
                                                        //pubTopic = pubTopic+"."+nameDO;
                                                        System.out.println("JSON Payload DO Type 1: " + jsonObject);


                                                        ArrayList<String> DAttributes = getDA(DataObjectTypes);

                                                        for (int f = 0; f < DAttributes.size(); f++) {
                                                            System.out.println("DA: " + DAttributes.get(f));
                                                            JSONObject jsondataDAttribute = XML.toJSONObject(DAttributes.get(f));
                                                            System.out.println("jsonDataAttribute: " + jsondataDAttribute);
                                                            String nameDA = jsondataDAttribute.getJSONObject("DA").getString("name");

                                                            pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType0 + "." + nameDO + "." + nameDA;

                                                            System.out.println("pubTopic dopo DataAttribute: " + pubTopic);
                                                            MqttMessage messageDA = new MqttMessage(jsondataDAttribute.toString().getBytes());
                                                            messageDA.setQos(qos);
                                                            messageDA.setRetained(true);
                                                            sampleClient.publish(pubTopic, messageDA);
                                                            topicDisponibili.add(pubTopic);
                                                        }

                                                        ;

                                                    }

                                                    //pubTopic = iedName + "\\" + logicalDeviceName + "\\"+lnType0;

                                                }

                                            } else {

                                                String idDO = jsondataDOTypes.getJSONObject("DOType").getString("id");
                                                System.out.println("DOT: "+DataObjectTypes);
                                                System.out.println(jsondataDOTypes);
                                                if (idDO.equals(typeDO)) {

                                                    ArrayList<String> DAttributes = getDA(DataObjectTypes);

                                                    for (int f = 0; f < DAttributes.size(); f++) {
                                                        System.out.println("DA: " + DAttributes.get(f));
                                                        JSONObject jsondataDAttribute = XML.toJSONObject(DAttributes.get(f));
                                                        System.out.println("jsonDataAttribute: " + jsondataDAttribute);
                                                        String nameDA = jsondataDAttribute.getJSONObject("DA").getString("name");

                                                        pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType0 + "." + nameDO + "." + nameDA;

                                                        System.out.println("pubTopic dopo DataAttribute: " + pubTopic);
                                                        MqttMessage messageDA = new MqttMessage(jsondataDAttribute.toString().getBytes());
                                                        messageDA.setQos(qos);
                                                        messageDA.setRetained(true);
                                                        sampleClient.publish(pubTopic, messageDA);
                                                        topicDisponibili.add(pubTopic);


                                                    }
                                                    //JSONArray DA = jsondataDOTypes.getJSONObject("DOType").getJSONArray("DA");
                                                    //System.out.println("DA: "+DA);
                                                    //pubTopic = iedName + "\\" + logicalDeviceName + "\\"+lnType0+"."+nameDO;
                                                    //System.out.println(pubTopic);
                                                    //MqttMessage messageDOType = new MqttMessage(jsondataDOTypes.toString().getBytes());
                                                    //messageDOType.setQos(qos);
                                                    //messageDOType.setRetained(true);
                                                    //sampleClient.publish(pubTopic, messageDOType);
                                                    //System.out.println("DOTYPE PUBBLICATO SU TOPIC: "+pubTopic+"\n");

                                                }

                                            }
                                        }
                                    }
                                }
                            }

                            /*ALTRI NODI LOGICI*/
                            for (int j = 0; j < lNodes.size(); j++) {
                                String logicalNode = lNodes.get(j);

                                JSONObject jsondata = XML.toJSONObject(logicalNode);

                                if (jsondata.get("LN") instanceof JSONArray) {

                                    JSONArray recs = jsondata.getJSONArray("LN");

                                    for (int k = 0; k < recs.length(); k++) {
                                        JSONObject rec = recs.getJSONObject(k);

                                        String jsonString = "{\"LN\":" + rec.toString() + "}";
                                        JSONObject jsonObject = new JSONObject(jsonString);

                                        pubTopic = iedName + "/" + logicalDeviceName + "/";
                                        lnType = jsonObject.getJSONObject("LN").getString("lnType");

                                        for (int q = 0; q < LNTypes.size(); q++) {
                                            String LNodeType = LNTypes.get(q);
                                            JSONObject jsondataLNType = XML.toJSONObject(LNodeType);
                                            String id = jsondataLNType.getJSONObject("LNodeType").getString("id");
                                            if (id.equals(lnType)) {
                                                pubTopic = pubTopic + id;
                                                System.out.println("JSON Payload 1: " + jsondataLNType);
                                                System.out.println(pubTopic + "\n");
                                                System.out.println(LNodeType+"\n");

                                                MqttMessage messageLNType = new MqttMessage(jsondataLNType.toString().getBytes());
                                                messageLNType.setQos(qos);
                                                messageLNType.setRetained(true);
                                                sampleClient.publish(pubTopic, messageLNType);
                                                topicDisponibili.add(pubTopic);

                                                ArrayList<String> dataObjects = getDO(LNodeType);
                                                for (int l = 0; l < dataObjects.size(); l++) {
                                                    String DataObject = dataObjects.get(l);
                                                    JSONObject jsondataDO = XML.toJSONObject(DataObject);

                                                    String nameDO = jsondataDO.getJSONObject("DO").getString("name");
                                                    String typeDO = jsondataDO.getJSONObject("DO").getString("type");

                                                    pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType+"."+nameDO;

                                                    System.out.println("DO: "+DataObject);
                                                    System.out.println(jsondataDO);
                                                    System.out.println(pubTopic+"\n");
                                                    System.out.println(nameDO);
                                                    /*Manca la publish*/
                                                    MqttMessage messageDO = new MqttMessage(jsondataDO.toString().getBytes());
                                                    messageDO.setQos(qos);
                                                    messageDO.setRetained(true);
                                                    sampleClient.publish(pubTopic, messageDO);
                                                    topicDisponibili.add(pubTopic);

                                                    ArrayList<String> DOTypes = getDOTypes(dataTypeTemplates);

                                                    for (int r = 0; r < DOTypes.size(); r++) {

                                                        String DataObjectTypes = DOTypes.get(r);

                                                        JSONObject jsondataDOTypes = XML.toJSONObject(DataObjectTypes);

                                                        if (jsondataDOTypes.get("DOType") instanceof JSONArray) {
                                                            JSONArray recs2 = jsondataDOTypes.getJSONArray("DOType");
                                                            for (int z = 0; z < recs2.length(); z++) {
                                                                JSONObject rec2 = recs2.getJSONObject(z);

                                                                String jsonString2 = "{\"DOType\":" + rec2.toString() + "}";
                                                                JSONObject jsonObject2 = new JSONObject(jsonString2);

                                                                System.out.println("jsonObject2: "+jsonObject2);

                                                                String idDO = jsonObject2.getJSONObject("DOType").getString("id");
                                                                //String typeDO = jsonObject2.getJSONObject()
                                                                if (idDO.equals(typeDO)) {
                                                                    System.out.println("JSON Payload DO Type: " + jsonObject);

                                                                    ArrayList<String> DAttributes = getDA(DataObjectTypes);

                                                                    for (int f = 0; f < DAttributes.size(); f++) {
                                                                        System.out.println("DA: " + DAttributes.get(f));
                                                                        JSONObject jsondataDAttribute = XML.toJSONObject(DAttributes.get(f));
                                                                        System.out.println("jsonDataAttribute: " + jsondataDAttribute);
                                                                        String nameDA = jsondataDAttribute.getJSONObject("DA").getString("name");

                                                                        pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType + "." + nameDO + "." + nameDA;

                                                                        System.out.println("pubTopic dopo DataAttribute 3: " + pubTopic+"\n\n\n");
                                                                    }

                                                                }
                                                            }
                                                        }
                                                        else {

                                                        }
                                                    }


                                                }
                                                //MqttMessage messageLNType = new MqttMessage(jsondataLNType.toString().getBytes());
                                                //messageLNType.setQos(qos);
                                                //messageLNType.setRetained(true);
                                                //sampleClient.publish(pubTopic, messageLNType);
                                                /*LA TIPIZZAZIONE DI QUESTO NODO LOGICO VERRA' PUBBLICATA SU QUESTO TOPIC*/

                                            }
                                        }
                                    }

                                } else {
                                    lnType = jsondata.getJSONObject("LN").getString("lnType");

                                    for (int q = 0; q < LNTypes.size(); q++) {
                                        String LNodeType = LNTypes.get(q);
                                        JSONObject jsondataLNType = XML.toJSONObject(LNodeType);
                                        String id = jsondataLNType.getJSONObject("LNodeType").getString("id");
                                        if (id.equals(lnType)) {
                                            pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType;
                                            System.out.println("JSON Payload 2: " + jsondataLNType);
                                            System.out.println(pubTopic + "\n");
                                            MqttMessage messageLNType = new MqttMessage(jsondataLNType.toString().getBytes());
                                            messageLNType.setQos(qos);
                                            messageLNType.setRetained(true);
                                            sampleClient.publish(pubTopic, messageLNType);
                                            topicDisponibili.add(pubTopic);

                                            System.out.println(LNodeType+"\n");

                                            ArrayList<String> dataObjects = getDO(LNodeType);
                                            for (int l = 0; l < dataObjects.size(); l++) {
                                                String DataObject = dataObjects.get(l);
                                                JSONObject jsondataDO = XML.toJSONObject(DataObject);

                                                String nameDO = jsondataDO.getJSONObject("DO").getString("name");
                                                String typeDO = jsondataDO.getJSONObject("DO").getString("type");

                                                pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType+"."+nameDO;

                                                System.out.println("pubTopic: " + pubTopic);
                                                System.out.println("json payload data object: " + jsondataDO);
                                                MqttMessage messageDO = new MqttMessage(jsondataDO.toString().getBytes());
                                                messageDO.setQos(qos);
                                                messageDO.setRetained(true);
                                                sampleClient.publish(pubTopic, messageDO);
                                                topicDisponibili.add(pubTopic);

                                                System.out.println("DO: "+DataObject);
                                                System.out.println(jsondataDO);
                                                System.out.println(pubTopic+"\n");
                                                System.out.println(nameDO);

                                                /*Data object types con i rispettivi data attribute*/
                                                ArrayList<String> DOTypes = getDOTypes(dataTypeTemplates);

                                                for (int r = 0; r < DOTypes.size(); r++) {

                                                    String DataObjectTypes = DOTypes.get(r);

                                                    JSONObject jsondataDOTypes = XML.toJSONObject(DataObjectTypes);

                                                    if (jsondataDOTypes.get("DOType") instanceof JSONArray) {

                                                        JSONArray recs = jsondataDOTypes.getJSONArray("DOType");

                                                        for (int z = 0; z < recs.length(); z++) {
                                                            JSONObject rec = recs.getJSONObject(z);

                                                            String jsonString = "{\"DOType\":" + rec.toString() + "}";
                                                            JSONObject jsonObject = new JSONObject(jsonString);

                                                            String idDO = jsonObject.getJSONObject("DOType").getString("id");
                                                            if (idDO.equals(typeDO)) {
                                                                //pubTopic = pubTopic+"."+nameDO;
                                                                System.out.println("JSON Payload DO Type 1: " + jsonObject);


                                                                ArrayList<String> DAttributes = getDA(DataObjectTypes);



                                                                for (int f = 0; f < DAttributes.size(); f++) {
                                                                    System.out.println("DA: " + DAttributes.get(f));
                                                                    JSONObject jsondataDAttribute = XML.toJSONObject(DAttributes.get(f));
                                                                    System.out.println("jsonDataAttribute: " + jsondataDAttribute);
                                                                    String nameDA = jsondataDAttribute.getJSONObject("DA").getString("name");

                                                                    pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType + "." + nameDO + "." + nameDA;

                                                                    System.out.println("pubTopic dopo DataAttribute 1: " + pubTopic+"\n\n\n");
                                                                    MqttMessage messageDA = new MqttMessage(jsondataDAttribute.toString().getBytes());
                                                                    messageDA.setQos(qos);
                                                                    messageDA.setRetained(true);
                                                                    sampleClient.publish(pubTopic, messageDA);
                                                                    topicDisponibili.add(pubTopic);
                                                                }

                                                                ;

                                                            }

                                                            //pubTopic = iedName + "\\" + logicalDeviceName + "\\"+lnType0;

                                                        }

                                                    } else {

                                                        ;
                                                        //mi serve la parte dei data object
                                                        //for
                                                        String idDO = jsondataDOTypes.getJSONObject("DOType").getString("id");
                                                        System.out.println("\n");
                                                        if (idDO.equals(typeDO)) {

                                                            ArrayList<String> DAttributes = getDA(DataObjectTypes);

                                                            for (int f = 0; f < DAttributes.size(); f++) {
                                                                System.out.println("DA: " + DAttributes.get(f));
                                                                JSONObject jsondataDAttribute = XML.toJSONObject(DAttributes.get(f));
                                                                System.out.println("jsonDataAttribute: " + jsondataDAttribute);
                                                                String nameDA = jsondataDAttribute.getJSONObject("DA").getString("name");

                                                                pubTopic = iedName + "/" + logicalDeviceName + "/" + lnType + "." + nameDO + "." + nameDA;

                                                                System.out.println("pubTopic dopo DataAttribute 2: " + pubTopic+"\n\n\n");
                                                                MqttMessage messageDA = new MqttMessage(jsondataDAttribute.toString().getBytes());
                                                                messageDA.setQos(qos);
                                                                messageDA.setRetained(true);
                                                                sampleClient.publish(pubTopic, messageDA);
                                                                topicDisponibili.add(pubTopic);


                                                            }
                                                            //JSONArray DA = jsondataDOTypes.getJSONObject("DOType").getJSONArray("DA");
                                                            //System.out.println("DA: "+DA);
                                                            //pubTopic = iedName + "\\" + logicalDeviceName + "\\"+lnType0+"."+nameDO;
                                                            //System.out.println(pubTopic);
                                                            //MqttMessage messageDOType = new MqttMessage(jsondataDOTypes.toString().getBytes());
                                                            //messageDOType.setQos(qos);
                                                            //messageDOType.setRetained(true);
                                                            //sampleClient.publish(pubTopic, messageDOType);
                                                            //System.out.println("DOTYPE PUBBLICATO SU TOPIC: "+pubTopic+"\n");

                                                        }
                                                    }

                                                }



                                            }

                                            /*LA TIPIZZAZIONE DI QUESTO NODO LOGICO VERRA' PUBBLICATA SU QUESTO TOPIC*/

                                        }
                                    }

                                }

                            }
                            /*fine altri nodi logici*/

                        }

                        for (int counter = 0; counter < topicDisponibili.size(); counter++) {
                            System.out.println(topicDisponibili.get(counter)+"\n");
                        }

                        break;

                    case READ_ALL_DATA_KEY:
                        System.out.print("Reading all data...");
                        try {
                            association.getAllDataValues();
                        } catch (ServiceError e) {
                            System.err.println("Service error: " + e.getMessage());
                        }
                        System.out.println("done");
                        break;
                    case GET_DATA_VALUES_KEY: {
                        if (serverModel == null) {
                            System.out.println("You have to retrieve the model before reading data.");
                            return;
                        }

                        FcModelNode fcModelNode = askForFcModelNode();

                        System.out.println("Sending GetDataValues request...");

                        try {
                            association.getDataValues(fcModelNode);
                        } catch (ServiceError e) {
                            System.out.println("Service error: " + e.getMessage());
                            return;
                        } catch (IOException e) {
                            System.out.println("Fatal error: " + e.getMessage());
                            return;
                        }

                        System.out.println("Successfully read data.");
                        System.out.println(fcModelNode);

                        break;
                    }
                    case CREATE_DATA_SET_KEY: {
                        System.out.println(
                                "Enter the reference of the data set to create (e.g. myld/MYLN0.dataset1): ");
                        String reference = actionProcessor.getReader().readLine();

                        System.out.println("How many entries shall the data set have: ");
                        String numberOfEntriesString = actionProcessor.getReader().readLine();
                        int numDataSetEntries = Integer.parseInt(numberOfEntriesString);

                        List<FcModelNode> dataSetMembers = new ArrayList<>();
                        for (int i = 0; i < numDataSetEntries; i++) {
                            dataSetMembers.add(askForFcModelNode());
                        }

                        DataSet dataSet = new DataSet(reference, dataSetMembers);
                        System.out.print("Creating data set..");
                        association.createDataSet(dataSet);
                        System.out.println("done");

                        break;
                    }
                    case DELETE_DATA_SET_KEY: {
                        System.out.println(
                                "Enter the reference of the data set to delete (e.g. myld/MYLN0.dataset1): ");
                        String reference = actionProcessor.getReader().readLine();

                        DataSet dataSet = serverModel.getDataSet(reference);
                        if (dataSet == null) {
                            throw new ActionException("Unable to find data set with the given reference.");
                        }
                        System.out.print("Deleting data set..");
                        association.deleteDataSet(dataSet);
                        System.out.println("done");

                        break;
                    }
                    case REPORTING_KEY: {
                        System.out.println("Enter the URCB reference: ");
                        String reference = actionProcessor.getReader().readLine();
                        Urcb urcb = serverModel.getUrcb(reference);
                        if (urcb == null) {
                            Brcb brcb = serverModel.getBrcb(reference);
                            if (brcb != null) {
                                throw new ActionException(
                                        "Though buffered reporting is supported by the library it is not yet supported by the console application.");
                            }
                            throw new ActionException("Unable to find RCB with the given reference.");
                        }

                        while (true) {
                            association.getRcbValues(urcb);
                            System.out.println();
                            System.out.println(urcb);
                            System.out.println();
                            System.out.println("What do you want to configure?");
                            System.out.println("1 - reserve");
                            System.out.println("2 - cancel reservation");
                            System.out.println("3 - enable");
                            System.out.println("4 - disable");
                            System.out.println("5 - set data set");
                            System.out.println("6 - set trigger options");
                            System.out.println("7 - set integrity period");
                            System.out.println("8 - send general interrogation");
                            System.out.println("0 - quit");
                            try {
                                int rcbAction = Integer.parseInt(actionProcessor.getReader().readLine());
                                switch (rcbAction) {
                                    case 0:
                                        return;
                                    case 1:
                                        System.out.print("Reserving RCB..");
                                        association.reserveUrcb(urcb);
                                        System.out.println("done");
                                        break;
                                    case 2:
                                        System.out.print("Canceling RCB reservation..");
                                        association.cancelUrcbReservation(urcb);
                                        System.out.println("done");
                                        break;
                                    case 3:
                                        System.out.print("Enabling reporting..");
                                        association.enableReporting(urcb);
                                        System.out.println("done");
                                        break;
                                    case 4:
                                        System.out.print("Disabling reporting..");
                                        association.disableReporting(urcb);
                                        System.out.println("done");
                                        break;
                                    case 5: {
                                        System.out.print("Set data set reference:");
                                        String dataSetReference = actionProcessor.getReader().readLine();
                                        urcb.getDatSet().setValue(dataSetReference);
                                        List<ServiceError> serviceErrors =
                                                association.setRcbValues(
                                                        urcb, false, true, false, false, false, false, false, false);
                                        if (serviceErrors.get(0) != null) {
                                            throw serviceErrors.get(0);
                                        }
                                        System.out.println("done");
                                        break;
                                    }
                                    case 6: {
                                        System.out.print(
                                                "Set the trigger options (data change, data update, quality change, interity, GI):");
                                        String triggerOptionsString = actionProcessor.getReader().readLine();
                                        String[] triggerOptionsStrings = triggerOptionsString.split(",");
                                        BdaTriggerConditions triggerOptions = urcb.getTrgOps();
                                        triggerOptions.setDataChange(
                                                Boolean.parseBoolean(triggerOptionsStrings[0]));
                                        triggerOptions.setDataUpdate(
                                                Boolean.parseBoolean(triggerOptionsStrings[1]));
                                        triggerOptions.setQualityChange(
                                                Boolean.parseBoolean(triggerOptionsStrings[2]));
                                        triggerOptions.setIntegrity(Boolean.parseBoolean(triggerOptionsStrings[3]));
                                        triggerOptions.setGeneralInterrogation(
                                                Boolean.parseBoolean(triggerOptionsStrings[4]));
                                        List<ServiceError> serviceErrors =
                                                association.setRcbValues(
                                                        urcb, false, false, false, false, true, false, false, false);
                                        if (serviceErrors.get(0) != null) {
                                            throw serviceErrors.get(0);
                                        }
                                        System.out.println("done");
                                        break;
                                    }
                                    case 7: {
                                        System.out.print("Specify integrity period in ms:");
                                        String integrityPeriodString = actionProcessor.getReader().readLine();
                                        urcb.getIntgPd().setValue(Long.parseLong(integrityPeriodString));
                                        List<ServiceError> serviceErrors =
                                                association.setRcbValues(
                                                        urcb, false, false, false, false, false, true, false, false);
                                        if (serviceErrors.get(0) != null) {
                                            throw serviceErrors.get(0);
                                        }
                                        System.out.println("done");
                                        break;
                                    }
                                    case 8:
                                        System.out.print("Sending GI..");
                                        association.startGi(urcb);
                                        System.out.println("done");
                                        break;
                                    default:
                                        System.err.println("Unknown option.");
                                        break;
                                }
                            } catch (ServiceError e) {
                                System.err.println("Service error: " + e.getMessage());
                            } catch (NumberFormatException e) {
                                System.err.println("Cannot parse number: " + e.getMessage());
                            }
                        }
                    }
                    default:
                        break;
                }
            } catch (Exception e) {
                throw new ActionException(e);
            }
        }

        private FcModelNode askForFcModelNode() throws IOException, ActionException {
            System.out.println("Enter reference (e.g. myld/MYLN0.do.da.bda): ");
            String reference = actionProcessor.getReader().readLine();
            System.out.println("Enter functional constraint of referenced node: ");
            String fcString = actionProcessor.getReader().readLine();

            Fc fc = Fc.fromString(fcString);
            if (fc == null) {
                throw new ActionException("Unknown functional constraint.");
            }

            ModelNode modelNode = serverModel.findModelNode(reference, Fc.fromString(fcString));
            if (modelNode == null) {
                throw new ActionException(
                        "A model node with the given reference and functional constraint could not be found.");
            }

            if (!(modelNode instanceof FcModelNode)) {
                throw new ActionException(
                        "The given model node is not a functionally constraint model node.");
            }

            FcModelNode fcModelNode = (FcModelNode) modelNode;
            return fcModelNode;
        }

        @Override
        public void quit() {
            System.out.println("** Closing connection.");
            association.close();
            return;
        }
    }
}
