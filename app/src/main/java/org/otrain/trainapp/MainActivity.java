package org.otrain.trainapp;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    ProgressBar progressBar1, progressBar2;
    ListView listView;
    ArrayList<Station> stationsListItems;
    BSSIDSListAdapter bssidsListAdapter;
    HashMap<String, String> map;

    WifiManager mainWifi;
    WifiReceiver receiverWifi;
    List<ScanResult> scanResults;
    String mSSID = "S-ISRAEL-RAILWAYS";
    //String mSSID = "Campus-Guest";
    String url = "https://etherpad.mozilla.org/ep/pad/export/hPjMFFUv1I/latest?format=txt";
    String serverUrl = "https://etherpad.mozilla.org/hPjMFFUv1I";
    boolean scanByClickFlag;

    protected void onPause() {
        unregisterReceiver(receiverWifi);
        super.onPause();
    }

    protected void onResume() {
        registerReceiver(receiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        if (!mainWifi.isWifiEnabled()) {
            toast("wifi is disabled..making it enabled");
            mainWifi.setWifiEnabled(true);
        }
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (ListView) findViewById(R.id.listView);
        listView.setEmptyView(findViewById(android.R.id.empty));
        stationsListItems = new ArrayList<>();
        bssidsListAdapter = new BSSIDSListAdapter();
        listView.setAdapter(bssidsListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                alertToEnterStation();
            }
        });

        progressBar1 = (ProgressBar) findViewById(R.id.progressBar1);
        progressBar2 = (ProgressBar) findViewById(R.id.progressBar2);

        TextView link = (TextView) findViewById(R.id.link);
        link.setText(serverUrl);
        Linkify.addLinks(link, Linkify.ALL);

        // Initiate wifi service manager
        mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        // wifi scaned value broadcast receiver
        receiverWifi = new WifiReceiver();

        progressBar1.setVisibility(View.INVISIBLE);
        progressBar2.setVisibility(View.INVISIBLE);

    }

    private void alertToEnterStation() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Station name");
        alert.setMessage("Please enter station name:");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                copyToClipboard(value);
            }
        });

        alert.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, "Set test wifi");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == 0) {
            final AlertDialog.Builder alert = new AlertDialog.Builder(this);
            final EditText input = new EditText(this);
            alert.setView(input);
            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String value = input.getText().toString().trim();
                    mSSID = value;
                    toast("scannig for " + value);
                }
            });
            alert.setNegativeButton("Cancel",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.cancel();
                        }
                    });
            alert.show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onScan(View view) {
        // Check for wifi is disabled
        if (!mainWifi.isWifiEnabled()) {
            toast("wifi is disabled..making it enabled");
            mainWifi.setWifiEnabled(true);
        }
        scanByClickFlag = true;
        mainWifi.startScan();
        progressBar2.setVisibility(View.VISIBLE);
        stationsListItems.clear();
        bssidsListAdapter.notifyDataSetChanged();
    }

    public void onGetContentFromServer(View view) {
        progressBar1.setVisibility(View.VISIBLE);
        new GetContentFromServer().execute();
    }

    // Broadcast receiver class called its receive method
    // when number of wifi connections changed

    class WifiReceiver extends BroadcastReceiver {

        // This method call when number of wifi connections changed
        public void onReceive(Context c, Intent intent) {

            if (!scanByClickFlag) {
                return;
            }
            scanByClickFlag = false;

            progressBar2.setVisibility(View.INVISIBLE);
            scanResults = mainWifi.getScanResults();

            for (ScanResult scanResult : scanResults) {

                if (mSSID.equals(scanResult.SSID)) {
                    Station station = new Station();
                    station.bssid = scanResult.BSSID;
                    stationsListItems.add(station);
                }

            }

            if (stationsListItems.size() > 0) {
                new GetContentFromServer().execute();
            }
        }

    }

    private void copyToClipboard(String stationName) {

        try {
            StringBuilder sb = new StringBuilder();
            for (Station station : stationsListItems) {
                if (station.bssid != null && station.bssid.length() > 0 && map != null && !map.containsKey(station.bssid)) {
                    sb.append(station.bssid);
                    sb.append(" ");
                    sb.append(stationName);
                    sb.append("\n");
                }
            }

            if (sb.toString().isEmpty()) {
                toast("nothing to copy..");
                return;
            }

            int sdk = android.os.Build.VERSION.SDK_INT;
            if (sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setText(sb.toString());
            } else {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("text label", sb.toString());
                clipboard.setPrimaryClip(clip);
            }
            toast(sb.toString() + "copied!");
        } catch (Exception e) {
            toast(e.toString());
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private class GetContentFromServer extends AsyncTask<Void, Void, String> {

        @Override
        protected void onPreExecute() {
            progressBar1.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... params) {

            return checkForStationName();

        }

        @Override
        protected void onPostExecute(String s) {

            progressBar1.setVisibility(View.INVISIBLE);
            bssidsListAdapter.notifyDataSetChanged();

        }

        public String checkForStationName() {
            String result = "";
            try {
                URLConnection conn = new URL(url).openConnection();

                InputStream in = conn.getInputStream();
                HashMap<String, String> tempMap = convertStreamToString(in);
                if (tempMap != null) {
                    map = tempMap;
                }
                if (stationsListItems != null) {
                    for (Station station : stationsListItems) {
                        if (station.bssid == null || station.bssid.length() == 0) {
                            station.stationName = "bssid is empty";
                        } else if (map.containsKey(station.bssid)) {
                            station.stationName = map.get(station.bssid);
                        } else {
                            station.stationName = "Not found for this BSSID";
                        }
                    }
                }
            } catch (Exception e) {
                result = "error occured while trying to connect to server: " + e.toString();
            }

            return result;
        }

        private HashMap<String, String> convertStreamToString(InputStream is) throws UnsupportedEncodingException {

            BufferedReader reader = new BufferedReader(new
                    InputStreamReader(is, "UTF-8"));

            HashMap<String, String> map = new HashMap<>();
            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] strs = line.split(" ");

                    if (strs.length > 0) {
                        String key = strs[0];

                        String value = "";
                        if (strs.length > 1) {
                            for (int i = 1; i < strs.length; i++) {
                                value += strs[i] + " ";
                            }
                        }
                        map.put(key, value);
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return map;
        }

    }


    class BSSIDSListAdapter extends BaseAdapter {


        LayoutInflater layoutInflater;

        public BSSIDSListAdapter() {
            layoutInflater = LayoutInflater.from(MainActivity.this);
        }


        @Override
        public int getCount() {
            return stationsListItems != null ? stationsListItems.size() : 0;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.bssid_raw, null);
            }

            StationViewHolder stationViewHolder = (StationViewHolder) convertView.getTag();

            if (stationViewHolder == null) {
                stationViewHolder = new StationViewHolder();

                stationViewHolder.textView1 = (TextView) convertView.findViewById(R.id.textView1);
                stationViewHolder.textView2 = (TextView) convertView.findViewById(R.id.textView2);

                convertView.setTag(stationViewHolder);
            }


            Station station = stationsListItems.get(position);

            stationViewHolder.textView1.setText(station.bssid);
            stationViewHolder.textView2.setText(station.stationName);

            return convertView;
        }
    }

    class Station {
        String bssid;
        String stationName;
    }

    static class StationViewHolder {
        TextView textView1;
        TextView textView2;
    }
}
