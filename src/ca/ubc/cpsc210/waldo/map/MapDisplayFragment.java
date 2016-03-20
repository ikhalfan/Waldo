package ca.ubc.cpsc210.waldo.map;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONException;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay.OnItemGestureListener;
import org.osmdroid.views.overlay.ItemizedOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.PathOverlay;
import org.osmdroid.views.overlay.SimpleLocationOverlay;

import android.R.drawable;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ca.ubc.cpsc210.waldo.R;
import ca.ubc.cpsc210.waldo.model.*;
import ca.ubc.cpsc210.waldo.translink.TranslinkService;
import ca.ubc.cpsc210.waldo.util.LatLon;
import ca.ubc.cpsc210.waldo.util.Segment;
import ca.ubc.cpsc210.waldo.waldowebservice.WaldoService;

/**
 * Fragment holding the map in the UI.
 * 
 * @author CPSC 210 Instructor
 */
public class MapDisplayFragment extends Fragment  {

	/**
	 * Log tag for LogCat messages
	 */
	private final static String LOG_TAG = "MapDisplayFragment";

	/**
	 * Location of some points in lat/lon for testing and for centering the map
	 */
	private final static GeoPoint ICICS = new GeoPoint(49.261182, -123.2488201);
	private final static GeoPoint CENTERMAP = ICICS;
	private Location userLoc;
	/**
	 * Preference manager to access user preferences
	 */
	private SharedPreferences sharedPreferences;

	/**
	 * View that shows the map
	 */
	private MapView mapView;

	/**
	 * Map controller for zooming in/out, centering
	 */
	private MapController mapController;


	private FindUserLocation locationFinder;
	// **************** Overlay fields **********************

	/**
	 * Overlay for the device user's current location.
	 */
	private SimpleLocationOverlay userLocationOverlay;

	/**
	 * Overlay for bus stop to board at
	 */
	private ItemizedIconOverlay<OverlayItem> busStopToBoardOverlay;

	/**
	 * Overlay for bus stop to disembark
	 */
	private ItemizedIconOverlay<OverlayItem> busStopToDisembarkOverlay;

	/**
	 * Overlay for Waldo
	 */
	private ItemizedIconOverlay<OverlayItem> waldosOverlay;

	/**
	 * Overlay for displaying bus routes
	 */
	private List<PathOverlay> routeOverlays;

	/**
	 * Selected bus stop on map
	 */
	private OverlayItem selectedStopOnMap;

	/**
	 * Bus selected by user
	 */
	private OverlayItem selectedBus;

	// ******************* Application-specific *****************

	/**
	 * Wraps Translink web service
	 */
	private TranslinkService translinkService;

	/**
	 * Wraps Waldo web service
	 */
	private WaldoService waldoService;

	/**
	 * Waldo selected by user
	 */
	private Waldo selectedWaldo;

	/*
	 * The name the user goes by
	 */
	private String userName;

	/**
	 * Route currently being recommended
	 */
	private BusRoute routeRec;
	// ***************** Android hooks *********************

	/**
	 * Help initialize the state of the fragment
	 */
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);

		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

		waldoService = new WaldoService();
		translinkService = new TranslinkService();
		routeOverlays = new ArrayList<PathOverlay>();

		initializeWaldo();

		System.out.println("Attempting to build FindUserLocation");
		locationFinder = new FindUserLocation();
		System.out.println("Succeeded in build");
		System.out.println("Attempting to Listen for location updates");
		// Start Listening for location updates
		LocationManager locationManager =(LocationManager)getActivity().getSystemService(getActivity().LOCATION_SERVICE);	
		System.out.println("Network Provider: " + locationManager.GPS_PROVIDER);
		locationFinder.getLocationManager().requestLocationUpdates(locationFinder.getLocationManager().GPS_PROVIDER, 0, 0, locationFinder);
		System.out.println("Now listening");
		System.out.println("Attempting to get first location");
		Location lastloc = locationFinder.getLocationManager().getLastKnownLocation(locationFinder.getLocationManager().GPS_PROVIDER);
		System.out.println("lastloc set");
		if (!(lastloc == null)){
			updateLocation(lastloc);
			System.out.println("Location set to: " + lastloc.getLatitude() + " " + lastloc.getLongitude());
		}
		else userLoc = null;
		initializeWaldo();



	}

	/**
	 * Initialize the Waldo web service
	 */
	private void initializeWaldo() {
		String s = null;
		new InitWaldo().execute(s);
	}

	/**
	 * Set up map view with overlays for buses, selected bus stop, bus route and
	 * current location.
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		if (mapView == null) {
			mapView = new MapView(getActivity(), null);

			mapView.setTileSource(TileSourceFactory.MAPNIK);
			mapView.setClickable(true);
			mapView.setBuiltInZoomControls(true);

			mapController = mapView.getController();
			mapController.setZoom(mapView.getMaxZoomLevel() - 4);
			mapController.setCenter(CENTERMAP);

			userLocationOverlay = createLocationOverlay();
			busStopToBoardOverlay = createBusStopToBoardOverlay();
			busStopToDisembarkOverlay = createBusStopToDisembarkOverlay();
			waldosOverlay = createWaldosOverlay();

			// Order matters: overlays added later are displayed on top of
			// overlays added earlier.
			mapView.getOverlays().add(waldosOverlay);
			mapView.getOverlays().add(busStopToBoardOverlay);
			mapView.getOverlays().add(busStopToDisembarkOverlay);
			mapView.getOverlays().add(userLocationOverlay);

		}

		return mapView;
	}

	/**
	 * Helper to reset overlays
	 */
	private void resetOverlays() {
		OverlayManager om = mapView.getOverlayManager();
		om.clear();
		om.addAll(routeOverlays);
		om.add(busStopToBoardOverlay);
		om.add(busStopToDisembarkOverlay);
		om.add(userLocationOverlay);
		om.add(waldosOverlay);
	}

	/**
	 * Helper to clear overlays
	 */
	private void clearOverlays() {
		waldosOverlay.removeAllItems();
		clearAllOverlaysButWaldo();
		OverlayManager om = mapView.getOverlayManager();
		om.add(waldosOverlay);
	}

	/**
	 * Helper to clear overlays, but leave Waldo overlay untouched
	 */
	private void clearAllOverlaysButWaldo() {
		if (routeOverlays != null) {
			routeOverlays.clear();
			busStopToBoardOverlay.removeAllItems();
			busStopToDisembarkOverlay.removeAllItems();

			OverlayManager om = mapView.getOverlayManager();
			om.clear();
			om.addAll(routeOverlays);
			om.add(busStopToBoardOverlay);
			om.add(busStopToDisembarkOverlay);
			om.add(userLocationOverlay);
		}
	}

	/**
	 * When view is destroyed, remove map view from its parent so that it can be
	 * added again when view is re-created.
	 */
	@Override
	public void onDestroyView() {
		((ViewGroup) mapView.getParent()).removeView(mapView);
		super.onDestroyView();
	}

	/**
	 * Shut down the various services
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	/**
	 * Update the overlay with user's current location. Request location
	 * updates.
	 */
	@Override
	public void onResume() {
		//Start listening again
		locationFinder.getLocationManager().requestLocationUpdates(locationFinder.getLocationManager().GPS_PROVIDER, 0, 0, locationFinder);

		initializeWaldo();

		super.onResume();
	}

	/**
	 * Cancel location updates.
	 */
	@Override
	public void onPause() {
		//Stop listening
		locationFinder.getLocationManager().removeUpdates(locationFinder);
		super.onPause();
	}

	/**
	 * Update the marker for the user's location and repaint.
	 */
	public void updateLocation(Location location) {
		double lat = location.getLatitude();
		double lon = location.getLongitude();
		System.out.println("Lat = " + lat + " Long = " + lon);
		userLocationOverlay.setLocation(new GeoPoint(location));
		userLoc = location;
		mapView.invalidate();
	}

	/**
	 * Save map's zoom level and centre.
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		if (mapView != null) {
			outState.putInt("zoomLevel", mapView.getZoomLevel());
			IGeoPoint cntr = mapView.getMapCenter();
			outState.putInt("latE6", cntr.getLatitudeE6());
			outState.putInt("lonE6", cntr.getLongitudeE6());
		}
	}

	/**
	 * Retrieve Waldos from the Waldo web service
	 */
	public void findWaldos() {
		clearOverlays();
		// Find out from the settings how many waldos to retrieve, default is 1
		String numberOfWaldosAsString = sharedPreferences.getString(
				"numberOfWaldos", "1");
		int numberOfWaldos = Integer.valueOf(numberOfWaldosAsString);
		new GetWaldoLocations().execute(numberOfWaldos);
		mapView.invalidate();
	}

	/**
	 * Clear waldos from view
	 */
	public void clearWaldos() {
		clearOverlays();
		mapView.invalidate();

	}

	// ******************** Overlay Creation ********************

	/**
	 * Create the overlay for bus stop to board at marker.
	 */
	private ItemizedIconOverlay<OverlayItem> createBusStopToBoardOverlay() {
		ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

		OnItemGestureListener<OverlayItem> gestureListener = new OnItemGestureListener<OverlayItem>() {

			/**
			 * Display bus stop description in dialog box when user taps stop.
			 * 
			 * @param index
			 *            index of item tapped
			 * @param oi
			 *            the OverlayItem that was tapped
			 * @return true to indicate that tap event has been handled
			 */
			@Override
			public boolean onItemSingleTapUp(int index, OverlayItem oi) {

				new AlertDialog.Builder(getActivity())
				.setPositiveButton(R.string.ok, new OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						if (selectedStopOnMap != null) {
							selectedStopOnMap.setMarker(getResources()
									.getDrawable(R.drawable.pin_blue));

							mapView.invalidate();
						}
					}
				}).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
				.show();

				oi.setMarker(getResources().getDrawable(R.drawable.pin_blue));
				selectedStopOnMap = oi;
				mapView.invalidate();
				return true;
			}

			@Override
			public boolean onItemLongPress(int index, OverlayItem oi) {
				// do nothing
				return false;
			}
		};

		return new ItemizedIconOverlay<OverlayItem>(
				new ArrayList<OverlayItem>(), getResources().getDrawable(
						R.drawable.pin_blue), gestureListener, rp);
	}

	/**
	 * Create the overlay for bus stop to disembark at marker.
	 */
	private ItemizedIconOverlay<OverlayItem> createBusStopToDisembarkOverlay() {
		ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

		OnItemGestureListener<OverlayItem> gestureListener = new OnItemGestureListener<OverlayItem>() {

			/**
			 * Display bus stop description in dialog box when user taps stop.
			 * 
			 * @param index
			 *            index of item tapped
			 * @param oi
			 *            the OverlayItem that was tapped
			 * @return true to indicate that tap event has been handled
			 */
			@Override
			public boolean onItemSingleTapUp(int index, OverlayItem oi) {

				new AlertDialog.Builder(getActivity())
				.setPositiveButton(R.string.ok, new OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						if (selectedStopOnMap != null) {
							selectedStopOnMap.setMarker(getResources()
									.getDrawable(R.drawable.pin_blue));

							mapView.invalidate();
						}
					}
				}).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
				.show();

				oi.setMarker(getResources().getDrawable(R.drawable.pin_blue));
				selectedStopOnMap = oi;
				mapView.invalidate();
				return true;
			}

			@Override
			public boolean onItemLongPress(int index, OverlayItem oi) {
				// do nothing
				return false;
			}
		};

		return new ItemizedIconOverlay<OverlayItem>(
				new ArrayList<OverlayItem>(), getResources().getDrawable(
						R.drawable.pin_blue), gestureListener, rp);
	}

	/**
	 * Create the overlay for Waldo markers.
	 */
	private ItemizedIconOverlay<OverlayItem> createWaldosOverlay() {
		ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());
		OnItemGestureListener<OverlayItem> gestureListener = new OnItemGestureListener<OverlayItem>() {

			/**
			 * Display Waldo point description in dialog box when user taps
			 * icon.
			 * 
			 * @param index
			 *            index of item tapped
			 * @param oi
			 *            the OverlayItem that was tapped
			 * @return true to indicate that tap event has been handled
			 */
			@Override
			public boolean onItemSingleTapUp(int index, OverlayItem oi) {

				selectedWaldo = waldoService.getWaldos().get(index);
				Date lastSeen = selectedWaldo.getLastUpdated();
				SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
						"MMM dd, hh:mmaa", Locale.CANADA);

				new AlertDialog.Builder(getActivity())
				.setPositiveButton(R.string.get_route,
						new OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0,
							int arg1) {

						// CPSC 210 STUDENTS. You must set
						// currCoord to
						// the user's current location.
						LatLon currCoord = null;

						// CPSC 210 Students: Set currCoord...

						LatLon destCoord = selectedWaldo
								.getLastLocation();

						new GetRouteTask().execute(currCoord,
								destCoord);

					}
				})
				.setNegativeButton(R.string.ok, null)
				.setTitle(selectedWaldo.getName())
				.setMessage(
						"Last seen  " + dateTimeFormat.format(lastSeen))
						.show();

				mapView.invalidate();
				return true;
			}

			@Override
			public boolean onItemLongPress(int index, OverlayItem oi) {
				// do nothing
				return false;
			}
		};

		return new ItemizedIconOverlay<OverlayItem>(
				new ArrayList<OverlayItem>(), getResources().getDrawable(
						R.drawable.map_pin_thumb_blue), gestureListener, rp);
	}

	/**
	 * Create overlay for a bus route.
	 */
	private PathOverlay createPathOverlay() {
		PathOverlay po = new PathOverlay(Color.parseColor("#cf0c7f"),
				getActivity());
		Paint pathPaint = new Paint();
		pathPaint.setColor(Color.parseColor("#cf0c7f"));
		pathPaint.setStrokeWidth(4.0f);
		pathPaint.setStyle(Style.STROKE);
		po.setPaint(pathPaint);
		return po;
	}

	/**
	 * Create the overlay for the user's current location.
	 */
	private SimpleLocationOverlay createLocationOverlay() {
		ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

		return new SimpleLocationOverlay(getActivity(), rp) {
			@Override
			public boolean onLongPress(MotionEvent e, MapView mapView) {
				new GetMessagesFromWaldo().execute();
				return true;
			}

		};
	}

	/**
	 * Plot endpoints
	 */
	private void plotEndPoints(Trip trip) {

		BusStop startStop = trip.getStart();
		BusRoute route = trip.getRoute();
		Bus busToCatchStart = null;
		for (Bus b : route.getBuses()){
			if (busToCatchStart == null){
				busToCatchStart = b;
			}
			else{
				if (b.getMinutesToDeparture() <= busToCatchStart.getMinutesToDeparture()){
					busToCatchStart = b;
				}
			}
		}




		GeoPoint pointStart = new GeoPoint(trip.getStart().getLatLon()
				.getLatitude(), trip.getStart().getLatLon().getLongitude());

		OverlayItem overlayItemStart = new OverlayItem(Integer.valueOf(
				trip.getStart().getNumber()).toString(),("Catch bus " + busToCatchStart.getRoute().getRouteNumber() + " departing in " + busToCatchStart.getMinutesToDeparture() + " minutes"), pointStart);
		GeoPoint pointEnd = new GeoPoint(trip.getEnd().getLatLon()
				.getLatitude(), trip.getEnd().getLatLon().getLongitude());
		OverlayItem overlayItemEnd = new OverlayItem(Integer.valueOf(
				trip.getEnd().getNumber()).toString(), trip.getEnd()
				.getDescriptionToDisplay(), pointEnd);
		busStopToBoardOverlay.removeAllItems();
		busStopToDisembarkOverlay.removeAllItems();

		busStopToBoardOverlay.addItem(overlayItemStart);
		busStopToDisembarkOverlay.addItem(overlayItemEnd);
	}

	/**
	 * Plot bus route onto route overlays
	 * 
	 * @param rte
	 *            : the bus route
	 * @param start
	 *            : location where the trip starts
	 * @param end
	 *            : location where the trip ends
	 */
	private void plotRoute(Trip trip) {

		// Put up the end points
		plotEndPoints(trip);
		BusStop start = trip.getStart();
		BusStop end = trip.getEnd();
		BusRoute route = trip.getRoute();
		LatLon startLatLon = start.getLatLon();
		LatLon endLatLon = end.getLatLon();
		PathOverlay overlay = createPathOverlay();
		List<Segment> segments = route.getSegments();
		Segment segToAdd = new Segment();
		System.out.println("Attempting to draw route: " + route.toString());
		for (Segment s : segments){
			overlay = createPathOverlay();

			segToAdd = new Segment();
			for (LatLon point : s){
				System.out.println("Testing point: " + point.getLatitude() + " " + point.getLongitude());
				if (LatLon.inbetween(point, startLatLon, endLatLon)){
					segToAdd.addPoint(point);
					System.out.println("Point within!");
				}
			}
			for (LatLon point : segToAdd){
				overlay.addPoint(new GeoPoint(point.getLatitude(), point.getLongitude()));
				System.out.println("Added Point: " + point.getLatitude() + " " + point.getLongitude());
			}
			routeOverlays.add(overlay);



		}

		for (Overlay o : routeOverlays){
			mapView.getOverlays().add(o);
		}

		// CPSC 210 STUDENTS: Complete the implementation of this method

		// This should be the last method call in this method to redraw the map
		mapView.invalidate();
	}

	/**
	 * Plot a Waldo point on the specified overlay.
	 */
	private void plotWaldos(List<Waldo> waldos) {

		// CPSC 210 STUDENTS: Complete the implementation of this method

		for (Waldo waldo : waldos) { 
			waldosOverlay.addItem(new OverlayItem(waldo.getName(), waldo.getLastUpdated().toString(), new GeoPoint(waldo.getLastLocation().getLatitude(), waldo.getLastLocation().getLongitude())));
		}


		mapView.invalidate();
	}

	/**
	 * Helper to create simple alert dialog to display message
	 * 
	 * @param msg
	 *            message to display in alert dialog
	 * @return the alert dialog
	 */
	private AlertDialog createSimpleDialog(String msg) {
		AlertDialog.Builder dialogBldr = new AlertDialog.Builder(getActivity());
		dialogBldr.setMessage(msg);
		dialogBldr.setNeutralButton(R.string.ok, null);
		return dialogBldr.create();
	}

	/**
	 * Asynchronous task to get a route between two endpoints. Displays progress
	 * dialog while running in background.
	 */
	private class GetRouteTask extends AsyncTask<LatLon, Void, Trip> {
		private ProgressDialog dialog = new ProgressDialog(getActivity());
		private LatLon startPoint;
		private LatLon endPoint;

		@Override
		protected void onPreExecute() {
			translinkService.clearModel();
			dialog.setMessage("Retrieving route...");
			dialog.show();
		}

		@Override
		protected Trip doInBackground(LatLon... routeEndPoints) {
			if (userLoc == null){
				return null;
			}
			TranslinkService TLService = translinkService;
			startPoint = new LatLon(userLoc.getLatitude(), userLoc.getLongitude());
			int distance = Integer.parseInt(sharedPreferences.getString("stopDistance", "500"));
			System.out.println("Radius to check: " + distance);


			//Get stops around user
			TLService.getBusStopsAround(startPoint, distance);
			Set<BusStop> userStops = TLService.getBusStops();
			String toReturn = "User Stops: ";
			for (BusStop bs : userStops){
				toReturn = toReturn + bs.toString() + " ||| ";
				translinkService.addBusStop(bs);
				translinkService.getBusEstimatesForStop(bs);
			}
			System.out.println(toReturn);
			TLService.clearModel();

			//Get stops around target
			System.out.println("Target Loc: " + routeEndPoints[1].toString());
			endPoint = routeEndPoints[1];
			TLService.getBusStopsAround(endPoint, distance);
			Set<BusStop> targetStops = TLService.getBusStops();
			toReturn = "Target Stops: ";
			for (BusStop bs : targetStops){
				toReturn = toReturn + bs.toString() + " ||| ";
				translinkService.addBusStop(bs);
				translinkService.getBusEstimatesForStop(bs);
			}
			System.out.println(toReturn);
			TLService.clearModel();

			//Check if lists are empty
			if((userStops.size() == 0) || (targetStops.size() == 0)){
				return null;
			}

			//Check if walking distance
			for (BusStop bs : userStops){
				if (targetStops.contains(bs)){
					return new Trip(bs, bs, null, null, true);	
				}
			}
			String relDirection = getRelDirection (startPoint,routeEndPoints[1] );
			System.out.println("Relative Direction: " + relDirection);

			//Get List of available routes for user
			Set<BusRoute> userRoutes = new HashSet<BusRoute>();

			for (BusStop bs: userStops){
				for (BusRoute br : bs.getRoutes()){
					if (!(userRoutes.contains(br))){
						userRoutes.add(br);
					}
				}
			}
			translinkService.addToRoutes(userRoutes);
			toReturn = "User routes available: ";
			for (BusRoute br : userRoutes){
				toReturn = toReturn + br.toString() + " ||| ";
			}
			System.out.println(toReturn);

			//Get List of available routes for Target
			Set<BusRoute> targetRoutes = new HashSet<BusRoute>();

			for (BusStop bs: targetStops){
				for (BusRoute br : bs.getRoutes()){
					if (!(targetRoutes.contains(br))){
						targetRoutes.add(br);
					}
				}
			}
			translinkService.addToRoutes(targetRoutes);
			toReturn = "Target routes available: ";
			for (BusRoute br : targetRoutes){
				toReturn = toReturn + br.toString() + " ||| ";
			}
			System.out.println(toReturn);




			//Check if User and target share a route
			Set<BusRoute> sharedRoutes = new HashSet<BusRoute>();
			for(BusRoute br : userRoutes){
				if (targetRoutes.contains(br)){
					sharedRoutes.add(br);
				}	
			}
			toReturn = "Shared Routes: ";
			for (BusRoute br : sharedRoutes){
				toReturn = toReturn + br.toString() + " ||| ";
			}
			System.out.println(toReturn);

			//Check if no routes are available
			if (sharedRoutes.size() == 0){
				System.out.println("No routes available");
				return null;
			}

			//Create list of good stops
			List<BusStop> goodUserStops = new LinkedList<BusStop>();

			for (BusStop bs : userStops){
				for (BusRoute br : bs.getRoutes()){
					if ((sharedRoutes.contains(br))){

						goodUserStops.add(bs);
					}
				}
			}

			//Create list of Target stops
			List<BusStop> goodTargetStops = new LinkedList<BusStop>();

			for (BusStop bs : targetStops){
				for (BusRoute br : bs.getRoutes()){
					if ((sharedRoutes.contains(br))){

						goodTargetStops.add(bs);
					}
				}
			}

			//Filter each list by relative direction
			BusRoute sampleRoute = null;
			Boolean keepGoing = true;
			for (BusRoute r : sharedRoutes ){
				if (keepGoing){
					sampleRoute = r;
					keepGoing = false;
				}
			}
			toReturn = "Good User Stops Unfiltered : ";
			for (BusStop bs : goodUserStops){
				toReturn = toReturn + bs.toString() + " ||| ";
			}
			System.out.println(toReturn);
			toReturn = "Good Target Stops Unfiltered : ";
			for (BusStop bs : goodTargetStops){
				toReturn = toReturn + bs.toString() + " ||| ";
			}
			System.out.println(toReturn);

			if ((goodUserStops.size() == 0) || (goodTargetStops.size() == 0)){
				return null;


			}

			goodUserStops = filterByDirection(goodUserStops, relDirection);
			goodTargetStops = filterByDirection(goodTargetStops, relDirection);

			toReturn = "Good User Stops : ";
			for (BusStop bs : goodUserStops){
				toReturn = toReturn + bs.toString() + " ||| ";
			}
			System.out.println(toReturn);
			toReturn = "Good Target Stops : ";
			for (BusStop bs : goodTargetStops){
				toReturn = toReturn + bs.toString() + " ||| ";
			}
			System.out.println(toReturn);

			if ((goodUserStops.size() == 0) || (goodTargetStops.size() == 0)){

				return null;


			}




			String routingType = sharedPreferences.getString("routingOptions", "closest_stop_me");
			Boolean atUser = (routingType.equals("closest_stop_me"));
			Trip tripToReturn = makeTrip(goodUserStops, goodTargetStops, sharedRoutes);
			System.out.println("Trip made: " + tripToReturn.getRoute().toString() + " " + tripToReturn.getRoute().toString());
			System.out.println("Trip start: " + tripToReturn.getStart().toString() + " End: " + tripToReturn.getEnd().toString());
			if (tripToReturn.getRoute() == null){
				if (atUser){
					for (BusStop bs : goodUserStops){
						goodUserStops.remove(bs);
						tripToReturn = makeTrip(goodUserStops, goodTargetStops, sharedRoutes);
						if (tripToReturn.getRoute() != null){
							System.out.println("Located route: " + tripToReturn.getRoute().toString());
							translinkService.parseKMZ(tripToReturn.getRoute());
							return tripToReturn;
						}		

					}}
				else{
					for (BusStop bs : goodTargetStops){
						System.out.println("Trying again");
						goodTargetStops.remove(bs);
						tripToReturn = makeTrip(goodUserStops, goodTargetStops, sharedRoutes);
						if (tripToReturn.getRoute() != null){
							System.out.println("Located route: " + tripToReturn.getRoute().toString());
							translinkService.parseKMZ(tripToReturn.getRoute());
							return tripToReturn;
						}	
					}

				}

				return null;	

			}


			System.out.println("Located route: " + tripToReturn.getRoute().toString());
			toReturn = "Busses : ";
			for (Bus b : tripToReturn.getRoute().getBuses()){
				toReturn = toReturn + b.getRoute() + " " + b.getDirection() + " ||| ";
			}
			System.out.println(toReturn);





			if(tripToReturn.getDirection() == null) {
				return null;
			}
			translinkService.parseKMZ(tripToReturn.getRoute());
			routeRec = tripToReturn.getRoute();


			return tripToReturn;






		}



		/**
		 *  Constructs the appropriate trip to connect the user and the target
		 * @param goodUserStopsList - the list of user stops to test to construct a trip
		 * @param goodTargetStopsList - the list of target stops to test to construct a trip 
		 * @param sharedRoutes - the list of routes shared between the user and the target's stops
		 * @return The trip which should be taken to connect to user with the target
		 */
		private Trip makeTrip(List<BusStop> goodUserStopsList,List<BusStop> goodTargetStopsList, Set<BusRoute> sharedRoutes) {
			String routingType = sharedPreferences.getString("routingOptions", "closest_stop_me");
			BusStop closestStop = null;
			Boolean atUser = (routingType.equals("closest_stop_me"));
			List<BusStop> otherStops = null;
			BusStop otherStop = null;
			BusRoute matchingRoute = null;
			String direction = null;
			if (atUser){
				closestStop = getClosestStop(goodUserStopsList);
				otherStops = goodTargetStopsList;
			}
			else{
				closestStop = getClosestStop(goodTargetStopsList);
				otherStops = goodUserStopsList;
			}

			for (BusRoute br : sharedRoutes){
				System.out.println("Finding matcher for" + br.toString());
				otherStop = findMatcher(closestStop, otherStops);

				if (!(otherStop == null )){
					translinkService.getBusEstimatesForStop(closestStop);
					translinkService.getBusEstimatesForStop(otherStop);

					matchingRoute = sharedRoute(closestStop, otherStop);



					if (matchingRoute != null){
						if (atUser){
							direction = getDir(closestStop, matchingRoute);
						}
						else direction = getDir(otherStop, matchingRoute);

					}


					System.out.println("Direction: " + direction);
					System.out.println("Matching Route: " + matchingRoute.toString());
					if (atUser){
						System.out.println("FirstStop: " + closestStop.toString());
						System.out.println("SecondStop: " + otherStop.toString());
						return new Trip(closestStop, otherStop, direction, matchingRoute, false);
					}
					else{
						System.out.println("FirstStop: " + otherStop.toString());
						System.out.println("SecondStop: " + closestStop.toString());
						return new Trip(otherStop, closestStop, direction, matchingRoute, false);
					}






				}



			}






			return null;
		}

		/**
		 *  Gets the direction of the bus with route matchingRoute at stop closestStop
		 * @param closestStop - The stop to return the direction of
		 * @param matchingRoute - the route to test
		 * @return a String representing the direction
		 */
		private String getDir(BusStop closestStop, BusRoute matchingRoute) {
			translinkService.getBusEstimatesForStop(closestStop);
			System.out.println("Getting dir");
			for (BusRoute br : closestStop.getRoutes()){
				System.out.println("Attemtiong Getting dir for: " + br.toString() );
				if (br.equals(matchingRoute)){
					System.out.println("Checking busses!");
					for (Bus b : br.getBuses()){
						System.out.println("Bus is:" + b.getRoute() + " in direction " + b.getDirection());

						return b.getDirection();

					}
					System.out.println("No busses found...");
				}


			}



			return null;
		}

		/**
		 * Determines the route shared by two stops
		 * @param closestStop - the first stop to check
		 * @param otherStop - the second stop to test
		 * @return - a route shared between the stops
		 */
		private BusRoute sharedRoute(BusStop closestStop, BusStop otherStop) {

			translinkService.getBusEstimatesForStop(closestStop);

			for (BusRoute br : closestStop.getRoutes()){
				if (otherStop.getRoutes().contains(br)){
					if (!(br.getRouteNumber().substring(0,1).equals("N"))){
						return br;
					}


				}	
			}
			return null;
		}
		/**
		 * Find the stop in otherStops which matches a route from closestStop
		 * @param closestStop - The stop to compare with
		 * @param otherStops - The list of stops to check
		 * @return - the matching stop from otherStops
		 */
		private BusStop findMatcher(BusStop closestStop, List<BusStop> otherStops) {

			for (BusRoute br : closestStop.getRoutes()){
				for (BusStop bs : otherStops){
					if (bs.getRoutes().contains(br)){
						return bs;
					}	
				}
			}

			return null;
		}
		/**
		 * Gets the closest stop to either the user o the target, depending on the settings 
		 * @param goodStopsList - the list of stops to search
		 * @return the closest stop
		 */
		private BusStop getClosestStop(List<BusStop> goodStopsList) {
			BusStop returnStop = null;
			Double distanceOfRecent = null;
			LatLon userLatLon = new LatLon(userLoc.getLatitude(), userLoc.getLongitude());
			for (BusStop bs : goodStopsList){
				if (returnStop == null){
					returnStop = bs;
					distanceOfRecent = LatLon.distanceBetweenTwoLatLon(userLatLon, bs.getLatLon());
				}
				else{
					if (distanceOfRecent >= LatLon.distanceBetweenTwoLatLon(userLatLon, bs.getLatLon())){
						returnStop = bs;
						distanceOfRecent = LatLon.distanceBetweenTwoLatLon(userLatLon, bs.getLatLon());
					}		
				}		
			}



			return returnStop;
		}

		/**
		 * 	Filters a list of stops, returning only those that have the requested direction
		 * @param stops - The list of stops to filter
		 * @param relDirection - the directions to check for, as a string
		 * @return - the filtered list of stops
		 */
		private List<BusStop> filterByDirection(List<BusStop> stops, String relDirection) {
			String checkDir = "";
			String toReturn = "";
			BusRoute checkRoute = null;
			Boolean keepGoing = true;
			List<BusStop> returnStops = new LinkedList<BusStop>();

			Boolean gotRoute = false;
			for (BusStop bs : stops){
				System.out.println("--------------------Checking: " + bs.toString());


				gotRoute = false;
				for (BusRoute route : bs.getRoutes()){
					if (!gotRoute){
						if (!(route.getRouteNumber().substring(0, 1).equals("N"))){
							checkRoute = route;
							gotRoute = true;
						}
					}


				}




				if (checkRoute != null){
					System.out.println("Checkroute: " + checkRoute.toString());
					toReturn = "Busses : ";
					for (Bus b : checkRoute.getBuses()){
						toReturn = toReturn + b.getRoute() + " " + b.getDirection() + " ||| ";
					}
					System.out.println(toReturn);

				}
				else System.out.println("Checkroute is null");





				if (!(checkRoute == null)){

					for (Bus b : checkRoute.getBuses()){
						System.out.println("***********************Checking bus: " + b.getRoute() + " with direction: " + b.getDirection());
						if (keepGoing){
							checkDir = b.getDirection().toLowerCase();
							System.out.println("CheckDir: " + checkDir);
							keepGoing = false;
						}
					}
					keepGoing = true;
					String firstChar = bs.getName().substring(0, 1).toLowerCase();
					String northSouth = relDirection.substring(0, 1).toLowerCase();
					String eastWest = relDirection.substring(1, 2).toLowerCase();
					if (firstChar.equals(northSouth) || firstChar.equals(eastWest)){
						System.out.println("Added based on first char");
						returnStops.add(bs);
					}
					else if (!(firstChar.equals("n") || firstChar.equals("s") || firstChar.equals("e") ||  firstChar.equals("w") )){



						System.out.println("Reldirection = " + relDirection);
						System.out.println("Checking North");
						if ((checkDir.equals("north")) && (relDirection.substring(0, 1).equals("N"))){

							returnStops.add(bs);
						}
						System.out.println("Checking South");

						if ((checkDir.equals("south")) && (relDirection.substring(0, 1).equals("S"))){

							returnStops.add(bs);
						}
						System.out.println("Checking East");
						System.out.println("Substring is: {" + relDirection.substring(1, 2) + "}");


						if ((checkDir.equals("east")) && (relDirection.substring(1, 2).equals("E"))){
							System.out.println("Added");
							returnStops.add(bs);
						}
						System.out.println("Checking West");
						if ((checkDir.equals("west")) && (relDirection.substring(1, 2).equals("W"))){

							returnStops.add(bs);
						}
					}

				}
			}

			return returnStops;
		}

		/**
		 * Gets the relative direction between two points
		 * @param start- the location of the first point
		 * @param end- The location of the second point
		 * @return- The direction from start to end
		 */
		private String getRelDirection(LatLon start, LatLon end) {

			Double startLat = start.getLatitude();
			Double startLong = start.getLongitude();
			Double endLat = end.getLatitude();
			Double endLong = end.getLongitude();
			if (endLat >= startLat){
				if (endLong >= startLong){
					return "NE";
				}
				else return "NW";
			}
			else{
				if (endLong >= startLong){
					return "SE";
				}
				else return "SW";

			}


		}

		@Override
		protected void onPostExecute(Trip trip) {
			dialog.dismiss();

			if (trip != null && !trip.inWalkingDistance()) {
				// Remove previous start/end stops
				busStopToBoardOverlay.removeAllItems();
				busStopToDisembarkOverlay.removeAllItems();

				// Removes all but the selected Waldo
				waldosOverlay.removeAllItems();
				List<Waldo> waldos = new ArrayList<Waldo>();
				waldos.add(selectedWaldo);
				plotWaldos(waldos);

				// Plot the route
				plotRoute(trip);

				// Move map to the starting location
				LatLon startPointLatLon = trip.getStart().getLatLon();
				mapController.setCenter(new GeoPoint(startPointLatLon
						.getLatitude(), startPointLatLon.getLongitude()));
				mapView.invalidate();




			} else if (trip != null && trip.inWalkingDistance()) {
				AlertDialog dialog = createSimpleDialog("You are in walking distance!");
				dialog.show();
			} else {
				AlertDialog dialog = createSimpleDialog("Unable to retrieve bus location info...");
				dialog.show();
			}
		}
	}

	/**
	 * Asynchronous task to initialize or re-initialize access to the Waldo web
	 * service.
	 */
	private class InitWaldo extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... arg0) {

			// Initialize the service passing the name of the Waldo to use. If
			// you have
			// passed an argument to this task, then it will be used as the
			// name, otherwise
			// nameToUse will be null
			String nameToUse = arg0[0];
			System.out.println("Name given: " + nameToUse);
			userName = waldoService.initSession(nameToUse);
			System.out.println("Session Initiated");
			return null;
		}

	}

	/**
	 * Asynchronous task to get Waldo points from Waldo web service. Displays
	 * progress dialog while running in background.
	 */
	private class GetWaldoLocations extends
	AsyncTask<Integer, Void, List<Waldo>> {
		private ProgressDialog dialog = new ProgressDialog(getActivity());

		@Override
		protected void onPreExecute() {
			dialog.setMessage("Retrieving locations of waldos...");
			dialog.show();
		}

		@Override
		protected List<Waldo> doInBackground(Integer... i) {
			Integer numberOfWaldos = i[0];
			return waldoService.getRandomWaldos(numberOfWaldos);
		}

		@Override
		protected void onPostExecute(List<Waldo> waldos) {
			dialog.dismiss();
			if (waldos != null) {
				plotWaldos(waldos);
			}
		}
	}

	private class FindUserLocation implements LocationListener{
		LocationManager locationManager = (LocationManager)getActivity().getSystemService(getActivity().LOCATION_SERVICE);

		public FindUserLocation(){
			locationManager = (LocationManager)getActivity().getSystemService(getActivity().LOCATION_SERVICE);	
		}

		public LocationManager getLocationManager(){
			return locationManager;
		}

		@Override
		public void onLocationChanged(Location loc) {
			updateLocation(loc);
		}

		@Override
		public void onProviderDisabled(String arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onProviderEnabled(String arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
			// TODO Auto-generated method stub

		}

	}

	/**
	 * Asynchronous task to get messages from Waldo web service. Displays
	 * progress dialog while running in background.
	 */
	private class GetMessagesFromWaldo extends AsyncTask<Void, Void, List<String>> {

		private ProgressDialog dialog = new ProgressDialog(getActivity());

		@Override
		protected void onPreExecute() {
			dialog.setMessage("Retrieving messages...");
			dialog.show();
		}

		@Override
		protected List<String> doInBackground(Void... params) {
			return waldoService.getMessages();
		}

		@Override
		protected void onPostExecute(List<String> messages) {

			TextView title = new TextView(getActivity());
			title.setText("Messages");
			title.setPadding(10, 10, 10, 10);
			title.setGravity(Gravity.CENTER);
			title.setTextColor(Color.BLACK);
			title.setTextSize(20);

			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setCustomTitle(title);

			builder.setCancelable(true);
			builder.setNegativeButton("OK", new DialogInterface.OnClickListener() {

				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});
			String messagesCombined = "";
			String name = null;
			String msg = null;
			int indexAt = 0;

			while (indexAt+2 <= messages.size()) {
				name = messages.get(indexAt);
				msg = messages.get(indexAt+1);
				indexAt = indexAt+2;
				messagesCombined = messagesCombined + "\n" + name + ": " + msg + "\n";	
			}

			if ((messages.isEmpty()) || msg == null) {
				messagesCombined = "There are no messages.";
			}


			builder.setMessage(messagesCombined);
			AlertDialog alert = builder.show();
			TextView messageText = (TextView)alert.findViewById(android.R.id.message);
			messageText.setGravity(Gravity.CENTER);
			messageText.setTextColor(Color.RED);

			dialog.dismiss();


		}
	} 
} 
