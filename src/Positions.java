import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;

import javax.swing.text.Document;
import javax.swing.text.Element;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class Positions {


    public static void main(String[] args) throws Exception {

        get();
    }

    public static void get() throws IOException, JSONException {


        boolean fetchAUVType = true;

        boolean fetchGliderType = true;

        boolean fetchDrifterType = true;

        boolean fetchShipType = true;

        boolean fetchAISType = true;
        final String USER_AGENT = "Mozilla/5.0";


        HashMap<String, String> typeRqstLst = new LinkedHashMap<>();
        if (fetchAUVType)
            typeRqstLst.put("auv/", "AUV");
        if (fetchGliderType)
            typeRqstLst.put("glider/", "GLIDER");
        if (fetchDrifterType)
            typeRqstLst.put("drifter/", "DRIFTER");
        if (fetchShipType)
            typeRqstLst.put("ship/", "SHIP");
        if (fetchAISType)
            typeRqstLst.put("uav/", "AIS");


        for (String typeRqst : typeRqstLst.keySet()) {

            String url = "http://odss.mbari.org/trackingdb/" + "positionOfType/" + typeRqst + "last/" + 1 + "h/data.html";


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
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();


            parsing(response.toString());

        }
    }

    private static void parsing(String resp) {

        org.jsoup.nodes.Document doc = Jsoup.parse(resp);
        Elements links = doc.select("td");
        if (links.size() > 5) {
            org.jsoup.nodes.Element vehicle = links.select("td").get(links.size() - 5);
            org.jsoup.nodes.Element latitude = links.select("td").get(links.size() - 3);
            org.jsoup.nodes.Element longitude = links.select("td").get(links.size() - 2);
            System.out.println("Vehicle:\t" + vehicle.toString() + "\nLatitude:\t" + latitude.toString() + "\nLongitude:\t" + longitude.toString());
        }


    }

}
