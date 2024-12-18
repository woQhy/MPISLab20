package com.example.mpistask08;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.library.BuildConfig;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String PREFS_NAME = "RoutePrefs";
    private static final String ROUTE_KEY = "saved_route";

    private FusedLocationProviderClient fusedLocationClient;
    private MapView mapView;
    private IMapController mapController;
    private List<GeoPoint> geoPoints = new ArrayList<>();
    private Polyline polyline;
    private GeoPoint currentLocation; // Теперь текущая локация определена как поле класса
    private Button buttonAuthor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);

        mapController = mapView.getController();
        mapController.setZoom(15.0);

        mapView.setMultiTouchControls(true);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestLocationPermission();
        restoreRoute();

        buttonAuthor = findViewById(R.id.authorBtn);
        buttonAuthor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAlertDialog("Разработал", getString(R.string.author));
            }
        });
    }

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            if (locationResult == null) {
                return;
            }

            for (Location location : locationResult.getLocations()) {
                // Обновляем текущую локацию
                currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                geoPoints.add(currentLocation);
                mapController.setCenter(currentLocation);

                if (polyline == null) {
                    polyline = new Polyline();
                    polyline.setWidth(15);
                    polyline.setColor(0xFFFF0000);
                    mapView.getOverlayManager().add(polyline);
                }
                polyline.setPoints(geoPoints);
                mapView.invalidate();

                // Удаляем предыдущие маркеры, чтобы отображался только последний
                mapView.getOverlays().removeIf(overlay -> overlay instanceof Marker);

                // Добавляем новый маркер для текущей позиции
                Marker marker = new Marker(mapView);
                marker.setPosition(currentLocation);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marker.setIcon(getResources().getDrawable(R.drawable.map_pin)); // Устанавливаем кастомный значок булавки
                mapView.getOverlays().add(marker);
                mapView.invalidate();
            }
            saveRoute();
        }
    };

    private void saveRoute() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        StringBuilder sb = new StringBuilder();

        for (GeoPoint point : geoPoints) {
            sb.append(point.getLatitude()).append(",").append(point.getLongitude()).append(";");
        }
        editor.putString(ROUTE_KEY, sb.toString());
        editor.apply();
    }

    private void restoreRoute() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedRoute = preferences.getString(ROUTE_KEY, "");

        if (!savedRoute.isEmpty()) {
            String[] points = savedRoute.split(";");
            for (String point : points) {
                String[] latLng = point.split(",");
                if (latLng.length == 2) {
                    double latitude = Double.parseDouble(latLng[0]);
                    double longitude = Double.parseDouble(latLng[1]);
                    GeoPoint geoPoint = new GeoPoint(latitude, longitude);
                    geoPoints.add(geoPoint);
                }
            }
            if (polyline == null) {
                polyline = new Polyline();
                polyline.setWidth(15);
                polyline.setColor(0xFFFF0000);
                mapView.getOverlayManager().add(polyline);
            }
            polyline.setPoints(geoPoints);
            mapView.invalidate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void showAlertDialog(String title, String message) {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
    }
}
