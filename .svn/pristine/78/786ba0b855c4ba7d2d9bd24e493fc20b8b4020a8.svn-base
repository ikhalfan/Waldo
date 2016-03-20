package ca.ubc.cpsc210.waldo.waldowebservice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.os.Message;
import ca.ubc.cpsc210.waldo.exceptions.WaldoException;
import ca.ubc.cpsc210.waldo.model.Waldo;
import ca.ubc.cpsc210.waldo.util.LatLon;

public class WaldoService {

	private final static String WALDO_WEB_SERVICE_URL = "http://kramer.nss.cs.ubc.ca:8080/";
	private String nameUsed;
	private String key;
	private List<Waldo> waldos;

	/**
	 * Constructor
	 */
	public WaldoService() {
		waldos = new ArrayList<Waldo>();
	}

	/**
	 * Initialize a session with the Waldo web service. The session can time out
	 * even while the app is active...
	 * 
	 * @param nameToUse
	 *            The name to go register, can be null if you want Waldo to
	 *            generate a name
	 * @return The name that Waldo gave you
	 */
	public String initSession(String nameToUse) {
		try {
			if (nameToUse == null){
				String text = "";
				String possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

				for( int i=0; i < 5; i++ ){
					Double place = Math.random() * possible.length();
					place = Math.floor(place);
					Integer intPlace = place.intValue();
					text += possible.charAt(intPlace);
				}
				nameToUse = text;
			}
			URL url = new URL("http://kramer.nss.cs.ubc.ca:8080/initsession/" + nameToUse);


			HttpURLConnection client = (HttpURLConnection) url.openConnection();
			client.setRequestProperty("accept", "application/json");
			InputStream in = client.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String returnString = br.readLine();
			System.out.println("NameUsed: " + nameToUse);
			System.out.println("ToParse: " + returnString);
			client.disconnect();
			JSONObject toParse = new JSONObject(returnString);
			try {
				String errorNo = toParse.getString("ErrorNumber");
				String errorMsg = toParse.getString("ErrorMessage");
				throw new WaldoException("#" + errorNo + "error: " + errorMsg);
			} catch (JSONException e) {
				System.out.println("All is good");
			}


			String toReturn = toParse.getString("Name");
			System.out.println("ToReturn: " + toReturn);
			key = toParse.getString("Key");
			System.out.println("Key: " + key);
			nameUsed = nameToUse;
			return toReturn;
		} catch (Exception e) {
			e.printStackTrace();
			throw new WaldoException("Unable to make JSON query: " + "http://kramer.nss.cs.ubc.ca:8080/initsession/" + nameToUse);			

		}



	}

	/**
	 * Get waldos from the Waldo web service.
	 * 
	 * @param numberToGenerate
	 *            The number of Waldos to try to retrieve
	 * @return Waldo objects based on information returned from the Waldo web
	 *         service
	 */
	@SuppressWarnings("null")
	public List<Waldo> getRandomWaldos(int numberToGenerate) {
		try {
			URL url = new URL("http://kramer.nss.cs.ubc.ca:8080/getwaldos/" + key + "/" + numberToGenerate);
			HttpURLConnection client = (HttpURLConnection) url.openConnection();
			client.setRequestProperty("accept", "application/json");
			InputStream in = client.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String returnString = br.readLine();
			System.out.println(returnString);
			client.disconnect();

			try {
				JSONObject checkToParse = new JSONObject(returnString);
				int errorNo = checkToParse.getInt("ErrorNumber");
				String errorMsg = checkToParse.getString("ErrorMessage");
				System.out.println("Error detected: " + errorNo);
				if (errorNo == 7){
					System.out.println("Reassigning Name: " + nameUsed);
					initSession(nameUsed);
					url = new URL("http://kramer.nss.cs.ubc.ca:8080/getwaldos/" + key + "/" + numberToGenerate);
					client = (HttpURLConnection) url.openConnection();
					client.setRequestProperty("accept", "application/json");
					in = client.getInputStream();
					br = new BufferedReader(new InputStreamReader(in));
					returnString = br.readLine();
					System.out.println(returnString);
					client.disconnect();
				}
				else throw new WaldoException("#" + errorNo + "error: " + errorMsg);
			} catch (JSONException e) {
				System.out.println("All is good");
			}
			JSONArray toParse = new JSONArray(returnString);
			int size = toParse.length();
			int indexAt = 0;
			List<Waldo> waldosToReturn = new ArrayList<Waldo>();
			JSONObject objToParse = null;
			String name = null;
			JSONObject latLonDate = null;
			Double lat = null;
			Double lon = null;
			Date lastUpdated = null;
			String arrayToParse = toParse.toString();
			String objectToParse = null;
			System.out.println("Array to parse: " + arrayToParse);
			while (((indexAt+1) <= size)) {



				objToParse = toParse.getJSONObject(indexAt);
				System.out.println("Got object");
				if (objToParse == null){
					System.out.println("OBJECT IS NULL");
				}
				objectToParse = objToParse.toString();
				System.out.println("Object to parse: " + objectToParse);
				indexAt = indexAt + 1;
				name = objToParse.getString("Name");
				System.out.println("Name = " + name);
				if (objToParse.get("Loc") == null){
					waldosToReturn.add(new Waldo(name, null, null));
					System.out.println("No location");
				}
				else{
					latLonDate = new JSONObject(objToParse.getString("Loc"));
					lat = latLonDate.getDouble("Lat");
					System.out.println("Lat = " + lat);
					lon = latLonDate.getDouble("Long");
					System.out.println("Long = " + lon);
					lastUpdated = new Date(latLonDate.getLong("Tstamp"));
					System.out.println("Timestamp = " + lastUpdated);
					waldosToReturn.add(new Waldo(name, lastUpdated, new LatLon(lat,lon)));
					System.out.println("Waldo added");
				}
				objToParse = null;
				name = null;
				latLonDate = null;
				lat = null;
				lon = null;
				lastUpdated = null;

				System.out.println("Values reset to null");
			}
			waldos.addAll(waldosToReturn);
			for (Waldo w : waldosToReturn){
				System.out.println("Returned:" + w.getName());
			}



			return waldosToReturn;
		} catch (Exception e) {
			e.printStackTrace();
			throw new WaldoException("http:kramer.nss.cs.ubc.ca:8080/initsession/" + key + "/" + numberToGenerate);		

		}}

	/**
	 * Return the current list of Waldos that have been retrieved
	 * 
	 * @return The current Waldos
	 */
	public List<Waldo> getWaldos() {

		return this.waldos;
	}

	/**
	 * Retrieve messages available for the user from the Waldo web service
	 * 
	 * @return A list of messages
	 * @throws JSONException 
	 */
	public List<String> getMessages() {

		List<String> msgs = new ArrayList<String>();

		try {
			System.out.println("Attempting to get messages");
			URL url = new URL("http://kramer.nss.cs.ubc.ca:8080/getmsgs/" + key + "/");
			System.out.println("Trying URL: " + url.toString());
			HttpURLConnection client = (HttpURLConnection) url.openConnection();
			client.setRequestProperty("accept", "application/json");
			InputStream in = client.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String returnString = br.readLine();
			System.out.println("Returned: " + returnString);
			client.disconnect();

			JSONObject messages = new JSONObject(returnString);
			JSONArray toParse = messages.getJSONArray("Messages");
			int size = toParse.length();
			int indexAt = 0;
			JSONObject objToParse = null;
			String name = null;
			String message = null;
			String arrayToParse = toParse.toString();
			String objectToParse = null;
			System.out.println("Array to parse: " + arrayToParse);

			while (indexAt < size) {
				objToParse = toParse.getJSONObject(indexAt);
				System.out.println("Got object");
				if (objToParse == null) {
					System.out.println("NO MESSAGES");
				}
				objectToParse = objToParse.toString();
				System.out.println("Object to parse: " + objectToParse);
				name = objToParse.getString("Name");
				message = objToParse.getString("Message");
				System.out.println("Name = " + name);
				System.out.println("Message = " + message);
				msgs.add(name);
				msgs.add(message);
				
				System.out.println("Messages added to list");

				name = null;
				message = null;
				indexAt = indexAt+1;
				System.out.println("Values reset to null");
			}
			for (String m : msgs) {
				System.out.println(m);
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return msgs;
	}
}