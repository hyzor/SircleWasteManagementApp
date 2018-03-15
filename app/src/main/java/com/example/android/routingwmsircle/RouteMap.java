package com.example.android.routingwmsircle;

import android.content.Context;
import android.graphics.Color;
import android.location.Address;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.mapsforge.map.datastore.MultiMapDataStore;
import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.routing.GraphHopperRoadManager;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.QuadTreeTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.PathOverlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * Created by Rahul Setty on 2/22/2018.
 */

public class RouteMap extends AppCompatActivity{

    // boolean flag to check if there is marker or not
    protected boolean flag;

    // text view
    TextView textView;

    // Address list with Latitude and Longitude
    List<Address> address = new ArrayList<>();

    // Optimal address
    List<Address> optimalAddress = new ArrayList<>();

    // cost matrix
    int cost[][];

    // Create a Map to map the costValue and the pathList
    Map<Integer,Integer> map= new HashMap<>();

    Map<Integer,Integer> map1 = new HashMap<>();



    MapView mapView = null;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //load/initialize the osmdroid configuration, this can be done
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        //inflate and create the map
        setContentView(R.layout.activity_mapview);
        textView = findViewById(R.id.text_view_map);


        // Initialize the address
        address = MainActivity.addressLatLong;

        // Create clusters for segmentation
        sweepAlgoClustering(address);

        // View of uppsala in map
        uppsalaMapView();

        // Setting markers for garbage collection
        flag = settingMarkerinMap();

        // Add scale bar
        scaleBar();

        // Find the optimal route
        optimalAddress = OptimalRoute(address);
        // Checking the routing part between two points with osmbonus
        // Needed to be executed in a separate thread due to Network activity
        ArrayList<GeoPoint> waypoints = new ArrayList<GeoPoint>();
        GeoPoint startPoint;
        GeoPoint endPoint;

        if(flag){
            for (int i = 0; i < optimalAddress.size(); i++){

                if((i + 1) == optimalAddress.size()){
                    break;
                }
                else{
                    startPoint = new GeoPoint(optimalAddress.get(i).getLatitude(),optimalAddress.get(i).getLongitude());
                    endPoint = new GeoPoint(optimalAddress.get(i+1).getLatitude(),optimalAddress.get(i+1).getLongitude());

                    waypoints.add(startPoint);
                    waypoints.add(endPoint);

                    new RoadMap().execute(waypoints);
                }
            }
        }
        else{
            textView.setText("No route available");
        }
        Log.v("Main activity","roadmanager test 1");

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // User clicked on a menu option in the app bar overflow menu
        switch (item.getItemId()) {
            case android.R.id.home:
                // Navigate back to parent activity (MainActivity)
//                NavUtils.navigateUpFromSameTask(this);
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sweepAlgoClustering(List<Address> address){
        
        double angle;

        // Map demand to polar angle
        Map<Double,Integer> mapDP = new HashMap<>();

        // First get all the polar angles for the points from the depot which is location 1
        for(int i = 0; i < address.size(); i++){

            if((i + 1) == address.size()){
                break;
            }
            else{
                angle = Math.atan2(address.get(i + 1).getLongitude() - address.get(0).getLongitude(),
                        address.get(i + 1).getLatitude() - address.get(0).getLatitude()) * 180 / Math.PI;

                Log.e(GeocodeConstants.TAG_ROUTE,"angle :" + angle);
                mapDP.put(angle,i);
            }
        }

        TreeMap<Double,Integer> treeMap = new TreeMap<>(mapDP);

        // Create a Map to map cluster and the list
        int c = 0, sum = 0;
        String cluster = "cluster" + c;
        Multimap<String,Integer> multimap = ArrayListMultimap.create();

        for(Map.Entry<Double,Integer> entry : treeMap.entrySet()){

            sum = sum + MainActivity.demandList.get(entry.getValue());
            Log.e(GeocodeConstants.TAG_ROUTE,"sum : " + sum);

            if( sum <= MainActivity.vehicleCapacity){
                multimap.put(cluster,entry.getValue());
            }
            else{
                c++;
                cluster = "cluster" + c;
                multimap.put(cluster,entry.getValue());
            }
        }


        for(int i = 0; i < c + 1; i++){
            String clust = "cluster" + i;
            Log.e(GeocodeConstants.TAG_ROUTE," cluster : "+multimap.get(clust));
        }


    }

    private List<Address> OptimalRoute(List<Address> address){

        // Create a cost matrix which specifies the distance between the locations
        cost = new int[address.size()][address.size()];

        // Matrix to store path of optimal route
//        path = new int[address.size()][address.size()];
        List<Integer> path_inter = new ArrayList<>();
        List<Integer> path = new ArrayList<>();

        GeoPoint startPoint;
        GeoPoint endPoint;

        // Initialize the cost matrix to -1
        for(int i = 0; i < address.size(); i++){
            for(int j = 0; j < address.size(); j++){
                cost[i][j] = -1;
            }
        }

        // Assign the distance between the Geo points to the cost matrix
        for(int i = 0; i < address.size(); i++){
            for(int j = 0; j < address.size(); j++){

                if(i == j){
                    cost[i][j] = 0;
                }
                else{
                    startPoint = new GeoPoint(address.get(i).getLatitude(),address.get(i).getLongitude());
                    endPoint = new GeoPoint(address.get(j).getLatitude(),address.get(j).getLongitude());

                    Location location1 = new Location("");
                    location1.setLatitude(startPoint.getLatitude());
                    location1.setLongitude(startPoint.getLongitude());

                    Location location2 = new Location("");
                    location2.setLatitude(endPoint.getLatitude());
                    location2.setLongitude(endPoint.getLongitude());
                    double distance = location1.distanceTo(location2);

                    Log.e(GeocodeConstants.TAG_ROUTE,"time location 1 : " );
                    Log.e(GeocodeConstants.TAG_ROUTE,"time location 2 : " + location2.getTime());

                    cost[i][j] = (int)distance;
                    Log.e(GeocodeConstants.TAG_ROUTE,"cost from " + i + " to " + j + " : " + cost[i][j]);
                }
            }
        }



        // Create a node array
        List<Integer> nodearr = new ArrayList<>();
        for(int i = 0; i < address.size() - 1; i++){
            nodearr.add(i + 1);
//            Log.e(GeocodeConstants.TAG_ROUTE,"Node arr : " + nodearr.get(i));
        }

        // Compute the Travelling salesman problem
        int result = TSP(0, nodearr);
        Log.e(GeocodeConstants.TAG_ROUTE,"Minimum route : " + result);

        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            Log.e(GeocodeConstants.TAG_ROUTE, entry.getKey() + " : " + entry.getValue());
        }

        for (Map.Entry<Integer, Integer> entry : map1.entrySet()) {
            Log.e(GeocodeConstants.TAG_ROUTE, "Path : "+entry.getKey() + " : " + entry.getValue());
        }

        int val = 0;
        // First, get the mapping elements and store in a list
        for(int i = 0; i < map.size(); i++){
            val = searchMap(result);
            Log.e(GeocodeConstants.TAG_ROUTE, "Value intermediate :" + val);

            if(val == 0){
                break;
            }
            else{
                path_inter.add(val);
                result = val;
            }
        }

        // display the contents of the path
//        for(int i = 0; i < path_inter.size(); i++){
//            Log.e(GeocodeConstants.TAG_ROUTE, "Path inter value : " + path_inter.get(i));
//        }

        // Now, get the path sequence
        for(int i = 0; i < path_inter.size(); i++)
        {
            Log.e(GeocodeConstants.TAG_ROUTE, "Path inter value : " + path_inter.get(i));
            for (Map.Entry<Integer, Integer> entry : map1.entrySet()) {

                Log.e(GeocodeConstants.TAG_ROUTE, "Enter map1 : " + entry.getKey());
                if(path_inter.get(i).equals(entry.getKey())){
                    Log.e(GeocodeConstants.TAG_ROUTE, "Path value: "+entry.getValue());
                    path.add(entry.getValue());
                }
            }
        }

        for(int i = 0; i < path.size(); i++){
            Log.e(GeocodeConstants.TAG_ROUTE, "Path inter value : " + path.get(i));
        }


//        for(int i =0; i < pathList.size();i++)
//        {
//            Log.e(GeocodeConstants.TAG_ROUTE,"path : "+pathList.get(i));
//        }

//        for(int i = 0; i < pathList.size(); i++){
//            Log.e(GeocodeConstants.TAG_ROUTE,"Path : " + i + " " + pathList.get(i));
//        }
//
//        // Need to find the sequence of path based on the minimum route
//        // Size of path list
//        int size = address.size() + nodearr.size();
//        Log.e(GeocodeConstants.TAG_ROUTE,"Size : " + size);
//        int pathListsize = nodearr.size() * (size);
//        Log.e(GeocodeConstants.TAG_ROUTE,"Path size : " + pathListsize);
//        int k;
//        List<Integer> minPath = new ArrayList<>();
//
//        // Search for the final cost for each path
//        for(int i = size - 1; i < pathListsize - 1; i = i + size){
//            if(result == pathList.get(i)){
//                Log.e(GeocodeConstants.TAG_ROUTE,"Location of min cost in array : " + i);
//                k = i - (size - 1);
//                Log.e(GeocodeConstants.TAG_ROUTE,"k value : " + k);
//                for(int j = k; j < address.size(); j++){
//                    Log.e(GeocodeConstants.TAG_ROUTE,"Final min path : " + pathList.get(j));
//                    minPath.add(pathList.get(j));
//                }
//
//            }
//        }
//
        // Return the new sequence of address
        List<Address> newAdd = new ArrayList<>();
        int k = 0;
        newAdd.add(address.get(0));
        for(int i = 0; i < path.size(); i++){
            k = path.get(i);
            newAdd.add(address.get(k));
        }
        Log.e(GeocodeConstants.TAG_ROUTE,"Optimal address size : " + newAdd.size());

        return newAdd;
    }

    int searchMap(int key){

        int value = 0;
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            Log.e(GeocodeConstants.TAG_ROUTE, entry.getKey() + " : " + entry.getValue());

            if(key == entry.getKey()){
                value =  entry.getValue();
                Log.e(GeocodeConstants.TAG_ROUTE, " step 1 : " + value);
            }
        }
        return value;
    }



    private int TSP(int start, List<Integer> nodearr){


        int costValue = 0;
        List<Integer> tempCost = new ArrayList<>();

        // Create an array for !intersection
        List<Integer> set1 = new ArrayList<>();
        List<Integer> set2;

        // Initail condition for tsp f(i,phi) = Cij
        if(nodearr.get(0) == -1){
            Log.e(GeocodeConstants.TAG_ROUTE,"Start : " + start);

//            Log.e(GeocodeConstants.TAG_ROUTE,"Cost value : " + cost[start][0]);
            return cost[start][0];
        }
        else{

            for(int i = 0; i < nodearr.size(); i++){

                set1.add(start);
                set1.add(nodearr.get(i));

                Log.e(GeocodeConstants.TAG_ROUTE,"Start : " + start);

                set2 = intersection(set1,nodearr);
                set1.clear();

                int x = TSP(nodearr.get(i),set2);
                costValue = cost[start][nodearr.get(i)] + x;

                map.put(costValue,x);
                map1.put(x,nodearr.get(i));

                tempCost.add(costValue);

            }
            Collections.sort(tempCost);
            Log.e(GeocodeConstants.TAG_ROUTE,"Cost value : " + tempCost.get(0));
            // return the minimum value
            return tempCost.get(0);
        }
    }

    private List<Integer> intersection(List<Integer> arr1, List<Integer> arr2){

        List<Integer> arrIntersect = new ArrayList<>();
        for(int i : arr2){
            if(!arr1.contains(i)){
                arrIntersect.add(i);
            }
        }
        if(arrIntersect.isEmpty()){
            arrIntersect.add(-1);
        }
        return arrIntersect;

    }

    private void uppsalaMapView(){

        mapView = (MapView) findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        IMapController mapController = mapView.getController();
        mapController.setZoom(13);
        GeoPoint startPoint = new GeoPoint(59.8586, 17.6389);
        mapController.setCenter(startPoint);


    }

    private boolean settingMarkerinMap(){

        ArrayList<OverlayItem> overlayItems = new ArrayList<OverlayItem>();

        // Check if there is at least 2 entry data in the database to make the route
        if(MainActivity.count > 1){

            for(int i = 0; i < address.size() ; i++){
                overlayItems.add(new OverlayItem("","Testing Location",
                        new GeoPoint(address.get(i).getLatitude(), address.get(i).getLongitude())));
            }

            ItemizedIconOverlay<OverlayItem> anotherItemizedIconOverlay
                    = new ItemizedIconOverlay<OverlayItem>(this, overlayItems, null);
            mapView.getOverlays().add(anotherItemizedIconOverlay);
            return true;
        }
        else{
            return false;
        }
    }

    private void scaleBar(){
        //Add Scale Bar
        ScaleBarOverlay myScaleBarOverlay = new ScaleBarOverlay(mapView);
        mapView.getOverlays().add(myScaleBarOverlay);
    }

    public void onResume(){
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        mapView.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    public void onPause(){
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        mapView.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }

    private class RoadMap extends AsyncTask<ArrayList<GeoPoint>,Integer,Polyline> {

        @Override
        protected Polyline doInBackground(ArrayList<GeoPoint>... waypoints) {

            RoadManager roadManager = new OSRMRoadManager(getApplicationContext());

            Road road = roadManager.getRoad(waypoints[0]);
            double mDuration = road.mDuration;

            Log.e(GeocodeConstants.TAG_ROUTE,"Duration : " + mDuration);
            Log.e(GeocodeConstants.TAG_ROUTE,"Length : " + road.mLength);

            Polyline roadOverlay = RoadManager.buildRoadOverlay(road);

            return  roadOverlay;
        }

        @Override
        protected void onPostExecute(Polyline result){
            mapView.getOverlays().add(result);
            mapView.invalidate();
        }
    }


}
