package org.wordcamp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wordcamp.adapters.WCDetailAdapter;
import org.wordcamp.db.DBCommunicator;
import org.wordcamp.networking.WPAPIClient;
import org.wordcamp.objects.WordCampDB;
import org.wordcamp.objects.speaker.SpeakerNew;
import org.wordcamp.objects.wordcampnew.WordCampNew;
import org.wordcamp.utils.ImageUtils;
import org.wordcamp.utils.WordCampUtils;
import org.wordcamp.wcdetails.SessionsFragment;
import org.wordcamp.wcdetails.SpeakerFragment;
import org.wordcamp.wcdetails.WordCampOverview;
import org.wordcamp.widgets.SlidingTabLayout;

/**
 * Created by aagam on 26/1/15.
 */
public class WordCampDetailActivity extends AppCompatActivity implements SessionsFragment.SessionFragmentListener,
        SpeakerFragment.SpeakerFragmentListener, WordCampOverview.WordCampOverviewListener {

    public WCDetailAdapter adapter;
    public Toolbar toolbar;
    public ViewPager mPager;
    public WordCampDB wcdb;
    public int wcid;
    public DBCommunicator communicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wcdb = (WordCampDB) getIntent().getSerializableExtra("wc");
        wcid = wcdb.getWc_id();
        setContentView(R.layout.activity_wordcamp_detail);
        communicator = new DBCommunicator(this);
        communicator.start();
        initGUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        communicator.close();
    }

    private void initGUI() {
        ViewCompat.setElevation(findViewById(R.id.header), getResources().getDimension(R.dimen.toolbar_elevation));
        toolbar = (Toolbar) findViewById(R.id.toolbar);

        adapter = new WCDetailAdapter(getSupportFragmentManager());
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(adapter);
        mPager.setOffscreenPageLimit(2);
        final int tabHeight = getResources().getDimensionPixelSize(R.dimen.tab_height);
        findViewById(R.id.pager_wrapper).setPadding(0, ImageUtils.getActionBarSize(this) + tabHeight, 0, 0);

        SlidingTabLayout slidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);
        slidingTabLayout.setCustomTabView(R.layout.tab_view, android.R.id.text1);

        slidingTabLayout.setSelectedIndicatorColors(getResources().getColor(R.color.accent));
        slidingTabLayout.setDistributeEvenly(true);
        slidingTabLayout.setViewPager(mPager);

        toolbar.setTitle(wcdb.getWc_title());
        toolbar.setSubtitle(WordCampUtils.getProperDate(wcdb));
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_attending:
                if (!wcdb.isMyWC) {
                    int recv = communicator.addToMyWC(wcid);
                    item.setIcon(R.drawable.ic_star_white_36dp);
                    wcdb.isMyWC = true;
                } else {
                    communicator.removeFromMyWCSingle(wcid);
                    item.setIcon(R.drawable.ic_star_outline_white_36dp);
                    wcdb.isMyWC = false;
                }
                break;
            case R.id.action_refresh:
                updateWordCampData();
                break;
            case android.R.id.home:
                finish();
                break;
            case R.id.item_menu_website:
                startWebIntent();
                break;
        }

        return true;
    }

    private void startWebIntent() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(wcdb.getUrl()));
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(browserIntent);
    }

    private void updateWordCampData() {
        String webURL = wcdb.getUrl();

        fetchSpeakersAPI(webURL);
        getSessionsFragment().startRefreshSession();
//        fetchSessionsAPI(webURL);
//        fetchOverviewAPI();
    }

    private void fetchOverviewAPI() {
        WPAPIClient.getSingleWC(this, wcid, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                super.onSuccess(statusCode, headers, response);
                Gson g = new Gson();
                WordCampNew wc = g.fromJson(response.toString(), WordCampNew.class);
                WordCampDB wordCampDB = new WordCampDB(wc, "");
                communicator.updateWC(wordCampDB);

                WordCampOverview overview = getOverViewFragment();
                if (overview != null) {
                    overview.updateData(wordCampDB);
                    Toast.makeText(getApplicationContext(), "Updated overview", Toast.LENGTH_SHORT).show();

                }
            }


            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                WordCampOverview overview = getOverViewFragment();
                if (overview != null) {
                    overview.stopRefreshOverview();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                WordCampOverview overview = getOverViewFragment();
                if (overview != null) {
                    overview.stopRefreshOverview();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                super.onFailure(statusCode, headers, responseString, throwable);
                WordCampOverview overview = getOverViewFragment();
                if (overview != null) {
                    overview.stopRefreshOverview();
                }
            }
        });
    }

    private void fetchSessionsAPI(String webURL) {
        WPAPIClient.getWordCampSchedule(this, webURL, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                super.onSuccess(statusCode, headers, response);
                Gson gson = new Gson();
                for (int i = 0; i < response.length(); i++) {
                    try {
                        org.wordcamp.objects.speaker.Session session = gson.fromJson(response.getJSONObject(i).toString(), org.wordcamp.objects.speaker.Session.class);
                        if (communicator != null) {
                            communicator.addSession(session, wcid);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                Toast.makeText(getApplicationContext(), "Updated sessions "/* + response.length()*/, Toast.LENGTH_SHORT).show();
                stopRefreshSession();
                if (response.length() > 0) {
                    updateSessionContent();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                stopRefreshSession();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                stopRefreshSession();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                super.onFailure(statusCode, headers, responseString, throwable);
                stopRefreshSession();
            }
        });
    }

    private void stopRefreshSession() {
        SessionsFragment fragment = getSessionsFragment();
        if (fragment != null) {
            fragment.stopRefreshSession();
        }
    }

    private void stopRefreshSpeaker() {
        SpeakerFragment fragment = getSpeakerFragment();
        if (fragment != null) {
            fragment.stopRefreshSpeaker();
        }

        updateSessionContent();
    }

    private void updateSessionContent() {
        SessionsFragment fragment = getSessionsFragment();
        if (fragment != null) {
            fragment.updateData();
        }
    }

    private void fetchSpeakersAPI(String webURL) {
        WPAPIClient.getWordCampSpeakers(this, webURL, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                super.onSuccess(statusCode, headers, response);
                try {
                    addUpdateSpeakers(response);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                stopRefreshSpeaker();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                stopRefreshSpeaker();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                super.onFailure(statusCode, headers, responseString, throwable);
                stopRefreshSpeaker();
            }
        });
    }

    public void addUpdateSpeakers(JSONArray array) throws JSONException {
        Gson gson = new Gson();
        for (int i = 0; i < array.length(); i++) {
            SpeakerNew skn = gson.fromJson(array.getJSONObject(i).toString(), SpeakerNew.class);
            communicator.addSpeaker(skn, wcid);
        }

        if (array.length() > 0) {
            SpeakerFragment fragment = getSpeakerFragment();
            if (fragment != null) {
                fragment.updateSpeakers(communicator.getAllSpeakers(wcid));
            }
        }
        Toast.makeText(getApplicationContext(), "Updated speakers", Toast.LENGTH_SHORT).show();
        stopRefreshSpeaker();
    }

    public SpeakerFragment getSpeakerFragment() {
        return (SpeakerFragment) adapter.getItemAt(2);
    }

    public SessionsFragment getSessionsFragment() {
        return (SessionsFragment) adapter.getItemAt(1);
    }

    public WordCampOverview getOverViewFragment() {
        return (WordCampOverview) adapter.getItemAt(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_wc_detail, menu);
        if (wcdb.isMyWC) {
            MenuItem attending = menu.findItem(R.id.action_attending);
            attending.setIcon(R.drawable.ic_star_white_36dp);
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (communicator == null) {
            communicator = new DBCommunicator(this);
        } else {
            communicator.restart();
            updateSessionContent();
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        WPAPIClient.cancelAllRequests(this);
        if (communicator != null)
            communicator.close();
    }

    @Override
    protected void onStop() {
        super.onStop();
        WPAPIClient.cancelAllRequests(this);
    }

    @Override
    public void startRefreshSessions() {
        //Even we are refreshing sessions,
        // we will fetch Speakers as we get Sessions from there

        if (getSpeakerFragment() != null) {
            getSpeakerFragment().startRefreshSession();
        }
    }

    @Override
    public void startRefreshSpeakers() {
        String webURL = wcdb.getUrl();
        fetchSessionsAPI(webURL);
        fetchSpeakersAPI(webURL);
        getSessionsFragment().startRefreshingBar();
    }

    @Override
    public void refreshOverview() {
        fetchOverviewAPI();
    }
}
