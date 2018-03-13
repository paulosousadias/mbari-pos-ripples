
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import javafx.util.Pair;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import pt.lsts.imc.RemoteSensorInfo;
import pt.lsts.imc.net.ConnectFilter;
import pt.lsts.imc.net.IMCProtocol;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;


public class Positions {

    private static int imcid;
    private static final LinkedHashMap<String, String> sensorClass = new LinkedHashMap<>();
    private static String sensorClass2;
    private static HashMap<String, String> typeRqstLst = new LinkedHashMap<>();
    private static double lastLat = 0;
    private static double lastLon = 0;
    private static double penumLat = 0;
    private static double penumLon = 0;
    private static IMCProtocol proto;
    public static void main(String[] args) throws Exception {

        get();
    }

    private static void get() throws IOException {


        boolean fetchAUVType = true;

        boolean fetchGliderType = true;

        boolean fetchDrifterType = true;

        boolean fetchShipType = true;

        boolean fetchAISType = true;
        final String USER_AGENT = "Mozilla/5.0";





        if (fetchAUVType){
            typeRqstLst.put("auv/", "AUV");

        }
        if (fetchGliderType) {
            typeRqstLst.put("glider/", "GLIDER");

        }
        if (fetchDrifterType) {
            typeRqstLst.put("drifter/", "DRIFTER");

        }
        if (fetchShipType) {
            typeRqstLst.put("ship/", "SHIP");
        }
        if (fetchAISType) {
            typeRqstLst.put("uav/", "AIS");

        }


        for (String typeRqst : typeRqstLst.keySet()) {

            String url = "http://odss.mbari.org/trackingdb/" + "positionOfType/" + typeRqst + "last/" + 1 + "h/data.html";

            sensorClass2 = typeRqst;


            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            //add request header
            con.setRequestProperty("User-Agent", USER_AGENT);

            int responseCode = con.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();


            parsing(response.toString());


        }
    }

    private static void parsing(String resp) throws IOException {
        LinkedHashMap<String, Pair<Double, Double>> vehicleData = new LinkedHashMap<>();

        String latitude;
        String longitude;
        String previous=null;
        String current;


        final LinkedHashMap<String, Integer> data = new LinkedHashMap<>();
        org.jsoup.nodes.Document doc = Jsoup.parse(resp);

        Elements links = doc.select("td");
        if (links.size() > 5) {

            for (int i = 0; i < links.size(); i = i + 5) {

                if (i == 0) {
                    previous = String.valueOf(links.get(i));
                    previous = previous.substring(previous.indexOf(">") + 1, previous.indexOf("/") - 1);

                }

                current = String.valueOf(links.get(i));
                current = current.substring(current.indexOf(">") + 1, current.indexOf("/") - 1);
                if (!previous.equals(current)) {
                    latitude = links.get(i - 3).toString();
                    latitude = latitude.replaceAll("[^\\.0123456789-]", "");
                    double lat = Double.parseDouble(latitude);
                    longitude = links.get(i - 2).toString();
                    longitude = longitude.replaceAll("[^\\.0123456789-]", "");
                    double lon = Double.parseDouble(longitude);
                    penumLon = lon;

                    vehicleData.put(previous, new Pair<>(lat, lon));

                }
                if (links.get(i + 4).equals(links.last())) {
                    penumLat = Double.parseDouble(links.get(i - 3).toString().replaceAll("[^\\.0123456789-]", ""));
                    penumLon = Double.parseDouble(links.get(i - 2).toString().replaceAll("[^\\.0123456789-]", ""));


                    latitude = links.get(i + 2).toString();
                    latitude = latitude.replaceAll("[^\\.0123456789-]", "");
                    double lat = Double.parseDouble(latitude);
                    lastLat = lat;
                    longitude = links.get(i + 3).toString();
                    longitude = longitude.replaceAll("[^\\.0123456789-]", "");
                    double lon = Double.parseDouble(longitude);
                    lastLon = lon;
                    vehicleData.put(current, new Pair<>(lat, lon));

                }
                previous = current;

                synchronized (data) {
                    data.put(current, ++imcid);
                }
                synchronized (sensorClass) {
                    sensorClass.put(current, String.valueOf(sensorClass2));
                }
            }

            publishToNeptus(vehicleData, data);
            publishToRipples(vehicleData, data);



        }


    }


    private static void publishToNeptus(LinkedHashMap<String, Pair<Double, Double>> vehicleData, LinkedHashMap<String, Integer> data) {


        if (proto == null) {
            proto = new IMCProtocol();
        }

        double distance = Math.hypot(lastLat - penumLon, lastLon - penumLon);

        RemoteSensorInfo info = new RemoteSensorInfo();
        System.out.println(data.keySet());
        for (String s : data.keySet()) {

            info.setLat(Math.toRadians(vehicleData.get(s).getKey()));
            info.setLon(Math.toRadians(vehicleData.get(s).getValue()));
            info.setHeading((float) Math.atan2(penumLat - lastLat, penumLon - lastLon));

            String setSensor = sensorClass.get(s).replace("/", "");
            info.setSensorClass(setSensor);
            info.setId(s);
            System.out.println("Posting " + info);

        }




        try {

            proto.setAutoConnect(ConnectFilter.CCUS_ONLY);
            proto.sendToPeers(info);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }



    private static void publishToRipples(LinkedHashMap<String, Pair<Double, Double>> vehicleData, LinkedHashMap<String, Integer> data) throws IOException {
        String ripplesUrl = "http://ripples.lsts.pt/api/v1/systems";
        SimpleDateFormat fmt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ssZ");

        JsonObject obj = new JsonObject();


        for (String s : data.keySet()) {
            obj.add("imcid", data.get(s));
            obj.add("name", s);
            obj.add("coordinates", new JsonArray().add(vehicleData.get(s).getKey()).add(vehicleData.get(s).getValue()));

            obj.add("iridium", "");
            obj.add("created_at", fmt.format(new Date()));
            obj.add("updated_at", fmt.format(new Date()));
        }

        System.out.println(obj);


        URL url = new URL(ripplesUrl);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        httpCon.setRequestMethod("PUT");
        httpCon.setRequestProperty("Content-Type", "application/json");
        OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
        obj.writeTo(out);
        out.close();
        httpCon.getInputStream();
        System.out.println("Response: "+httpCon.getResponseMessage());

    }





}

