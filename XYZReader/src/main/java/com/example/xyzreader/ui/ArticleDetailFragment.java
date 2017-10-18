package com.example.xyzreader.ui;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.Loader;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;

import static android.view.View.GONE;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    public static final String ARG_ITEM_ID = "item_id";
    private static final String TAG = "ArticleDetailFragment";
    private static final float PARALLAX_FACTOR = 1.25f;

    private Cursor mCursor;
    private long mItemId;
    private View mRootView;
    private int mMutedColor = 0xFF333333;
    private ObservableScrollView mScrollView;
    private DrawInsetsFrameLayout mDrawInsetsFrameLayout;
    private ColorDrawable mStatusBarColorDrawable;

    private int mTopInset;
    private View mPhotoContainerView;
    private ImageView mPhotoView;
    private int mScrollY;
    private boolean mIsCard = false;
    private int mStatusBarFullOpacityBottom;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2, 1, 1);

    /**
     * Body Text Pagination Views
     */
    private RecyclerView mBodyTextRv;
    private BodyTextAdapter mBodyTextAdapter;
    private LinearLayoutManager mBodyTextRvLayoutManager;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    static float progress(float v, float min, float max) {
        return constrain((v - min) / (max - min), 0, 1);
    }

    static float constrain(float val, float min, float max) {
        if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        } else {
            return val;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }

        mIsCard = getResources().getBoolean(R.bool.detail_is_card);
        mStatusBarFullOpacityBottom = getResources().getDimensionPixelSize(
                R.dimen.detail_card_top_margin);
        setHasOptionsMenu(true);
    }

    public ArticleDetailActivity getActivityCast() {
        return (ArticleDetailActivity) getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        mDrawInsetsFrameLayout = (DrawInsetsFrameLayout)
                mRootView.findViewById(R.id.draw_insets_frame_layout);
        mDrawInsetsFrameLayout.setOnInsetsCallback(new DrawInsetsFrameLayout.OnInsetsCallback() {
            @Override
            public void onInsetsChanged(Rect insets) {
                mTopInset = insets.top;
            }
        });

        mScrollView = (ObservableScrollView) mRootView.findViewById(R.id.scrollview);
        mScrollView.setCallbacks(new ObservableScrollView.Callbacks() {
            @Override
            public void onScrollChanged() {
                mScrollY = mScrollView.getScrollY();
                getActivityCast().onUpButtonFloorChanged(mItemId, ArticleDetailFragment.this);
                mPhotoContainerView.setTranslationY((int) (mScrollY - mScrollY / PARALLAX_FACTOR));
                updateStatusBar();
            }
        });

        mPhotoView = (ImageView) mRootView.findViewById(R.id.photo);
        mPhotoContainerView = mRootView.findViewById(R.id.photo_container);

        mStatusBarColorDrawable = new ColorDrawable(0);

        mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });

        // TODO [Pagination Feature] Body Text recyclerView and related components
        mBodyTextRv = mRootView.findViewById(R.id.body_text_rv);
        mBodyTextAdapter = new BodyTextAdapter();
        mBodyTextRvLayoutManager = new LinearLayoutManager(getContext(),
                LinearLayoutManager.VERTICAL,false);
        mBodyTextRv.setAdapter(mBodyTextAdapter);
        mBodyTextRv.setLayoutManager(mBodyTextRvLayoutManager);

        bindViews();
        updateStatusBar();
        return mRootView;
    }

    private void updateStatusBar() {
        int color = 0;
        if (mPhotoView != null && mTopInset != 0 && mScrollY > 0) {
            float f = progress(mScrollY,
                    mStatusBarFullOpacityBottom - mTopInset * 3,
                    mStatusBarFullOpacityBottom - mTopInset);
            color = Color.argb((int) (255 * f),
                    (int) (Color.red(mMutedColor) * 0.9),
                    (int) (Color.green(mMutedColor) * 0.9),
                    (int) (Color.blue(mMutedColor) * 0.9));
        }
        mStatusBarColorDrawable.setColor(color);
        mDrawInsetsFrameLayout.setInsetBackground(mStatusBarColorDrawable);
    }

    private Date parsePublishedDate() {
        try {
            Log.i(TAG, "passing today's date");
            String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
            return dateFormat.parse(date);
        } catch (ParseException ex) {
            Log.e(TAG, ex.getMessage());
            return new Date();
        }
    }

    private void bindViews() {
        if (mRootView == null) {
            return;
        }

        TextView titleView = mRootView.findViewById(R.id.article_title);
        TextView bylineView = mRootView.findViewById(R.id.article_byline);
        bylineView.setMovementMethod(new LinkMovementMethod());

        // TODO [SPECIFICATION] App uses fonts that are either the Android defaults, are complementary, and aren't otherwise distracting.
        // Commented it out and use the system default font family roboto instead
        // bodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Rosario-Regular.ttf"));

        if (mCursor != null) {
            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1);

            titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                bylineView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(),
                                DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + " by <font color='#ffffff'>"
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));

            } else {
                // If date is before 1902, just show the string

                // Use Strubg resource with place holder instead
                bylineView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate) + " by <font color='#ffffff'>"
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));

            }

            // TODO [Pagination Feature] ! Performance issue: Body Text is GIGANTIC and freezes UI
            String mBodyTextStr = Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY)
                            .replaceAll("(\r\n\r\n)", "\\$")).toString();
            // thread with setting all the text at once, implemented methods to
            // offload the html parsing into spanned in the background with asyncTask
            mBodyTextAdapter.setBodyText(mBodyTextStr);

            // TODO [Use SwipeRefreshLayout to handle user load more request]







            ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                    .get(mCursor.getString(ArticleLoader.Query.PHOTO_URL), new ImageLoader.ImageListener() {
                        @Override
                        public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                            Bitmap bitmap = imageContainer.getBitmap();
                            if (bitmap != null) {
                                Palette.Builder pBuilder = new Palette.Builder(bitmap)
                                        .maximumColorCount(12);
                                Palette p = pBuilder.generate();

                                mMutedColor = p.getDarkMutedColor(0xFF333333);
                                mPhotoView.setImageBitmap(imageContainer.getBitmap());
                                mRootView.findViewById(R.id.meta_bar)
                                        .setBackgroundColor(mMutedColor);
                                updateStatusBar();
                            }
                        }

                        @Override
                        public void onErrorResponse(VolleyError volleyError) {

                        }
                    });
        } else {
            mRootView.setVisibility(GONE);
            titleView.setText("N/A");
            bylineView.setText("N/A");
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }

        bindViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }

    public int getUpButtonFloor() {
        if (mPhotoContainerView == null || mPhotoView.getHeight() == 0) {
            return Integer.MAX_VALUE;
        }

        // account for parallax
        return mIsCard
                ? (int) mPhotoContainerView.getTranslationY() + mPhotoView.getHeight() - mScrollY
                : mPhotoView.getHeight() - mScrollY;
    }

    /**
     * Pagination for the BodyText to avoid freezing UI in the setText
     * RecyclerView Adapter and a single TextView viewHolder
     */
    private class BodyTextAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private String mBodyText;
        private int startPosition;
        private final int snippetLength = 1000;
        private ArrayList<String> mSnippets;

        // Using footer for the recyclerView to show loading progress bar
        private static final int TYPE_ITEM = 1;
        private static final int TYPE_FOOTER = 2;

        BodyTextAdapter() {
            mSnippets = new ArrayList<>();
        }

        void setBodyText(String mBodyText)
        {
            this.mBodyText = mBodyText;
            // fire off method to split the bodyText into snippets
            fetchBodyTextSnippet();
        }

        void addSnippets(String[] snippetsArray)
        {
            int currentPosition = getItemCount();
            Collections.addAll(mSnippets,snippetsArray);
            notifyItemRangeInserted(currentPosition,mSnippets.size());
        }

        /**
         * AsyncTask method to load the body text into the adapter in chucks of chars
         * instead of all at once
         */
        private void fetchBodyTextSnippet() {
            // No or no more bodyText to fetch, terminate
            if (mBodyText == null || startPosition >= mBodyText.length()) return;

            new AsyncTask<Void, Void, String[]>() {
                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                }

                @Override
                protected String[] doInBackground(Void... voids) {
                    // Get the substring that is with at least n chars and ends with \\$
                    int endPosition = mBodyText.indexOf("$", startPosition + snippetLength);
                    if (endPosition >= mBodyText.length() || endPosition == -1) // if not found
                        endPosition = mBodyText.length();

                    // Get the substring with the starting and ending position
                    String mBodyTextStr = mBodyText.substring(startPosition,endPosition);

                    // Assign the endingPosition as the next starting position
                    startPosition = endPosition;

                    // return an array of sub-strings for the recyclerView adapter
                    return mBodyTextStr.split("\\$");
                }

                @Override
                protected void onPostExecute(String[] snippets) {
                    super.onPostExecute(snippets);
                    addSnippets(snippets);
                }

            }.execute();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if(viewType == TYPE_FOOTER) {
                View view = getLayoutInflater().inflate(R.layout.footer_item_read_more, parent, false);
                return new FooterViewHolder(view);
            } else if(viewType == TYPE_ITEM) {
                View view = getLayoutInflater().inflate(R.layout.list_item_body_text_snippet, parent, false);
                return new SnippetViewHolder(view);
            }
            return null;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (mSnippets.size() == 0) return;
            if (holder instanceof SnippetViewHolder) {
                SnippetViewHolder viewHolder = (SnippetViewHolder) holder;
                String snippet = mSnippets.get(position);
                viewHolder.snippetTv.setText(snippet);
            }else if (holder instanceof FooterViewHolder)
            {
                FooterViewHolder viewHolder = (FooterViewHolder) holder;
                // Hide the readMoreBtn when the user finishes
                viewHolder.readMoreBtn.setVisibility(startPosition == mBodyText.length()? View.GONE: View.VISIBLE);
                viewHolder.readMoreBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // load more text for user to read
                        fetchBodyTextSnippet();
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return mSnippets.size() + 1;
        }

        @Override
        public int getItemViewType (int position) {
            if(isPositionFooter (position)) {
                return TYPE_FOOTER;
            }
            return TYPE_ITEM;
        }

        private boolean isPositionFooter (int position) {
            return position == mSnippets.size();
        }
    }

    private static class SnippetViewHolder extends RecyclerView.ViewHolder {
        private TextView snippetTv;

        SnippetViewHolder(View view) {
            super(view);
            snippetTv = view.findViewById(R.id.list_item_body_text_snippet);
        }
    }

    private static class FooterViewHolder extends RecyclerView.ViewHolder {
        private Button readMoreBtn;

        FooterViewHolder(View view) {
            super(view);
            readMoreBtn = view.findViewById(R.id.read_more_btn);
        }
    }
}
