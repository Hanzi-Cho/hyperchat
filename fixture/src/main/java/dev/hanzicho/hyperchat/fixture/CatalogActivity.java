package dev.hanzicho.hyperchat.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A fixed book catalog whose results and detail pages depend on the entered query. */
public final class CatalogActivity extends Activity {
    private static final String[] TITLES = { "달빛 정원", "달빛 정원 산책", "별빛 도서관" };
    private LinearLayout layout;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int inset = (int)(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(inset, inset * 2, inset, inset);
        setContentView(layout);
        addTitle("도서관 카탈로그", View.NO_ID);
        addButton("도서 검색", R.id.catalog_entry, this::openCatalogSearch);
    }

    private void openCatalogSearch() {
        layout.removeAllViews();
        addTitle("도서 검색", View.NO_ID);
        EditText title = new EditText(this);
        title.setId(R.id.catalog_title_field);
        title.setSingleLine(true);
        title.setHint("도서 제목");
        title.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        layout.addView(title);
        addButton("검색", R.id.catalog_query_button, () -> showMatches(title));
        title.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            showMatches(title);
            return true;
        });
    }

    private void showMatches(EditText title) {
        String query = title.getText().toString().trim();
        if (query.isEmpty()) return;
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(title.getWindowToken(), 0);
        layout.removeAllViews();
        addTitle("찾은 책", R.id.catalog_matches);
        for (String book : TITLES) {
            if (book.contains(query)) {
                addButton(book, View.NO_ID, () -> showDetail(book, query));
            }
        }
    }

    private void showDetail(String book, String query) {
        layout.removeAllViews();
        addTitle("도서 상세: " + book, R.id.catalog_detail);
        TextView submittedQuery = new TextView(this);
        submittedQuery.setId(R.id.catalog_detail_query);
        submittedQuery.setText("검색어: " + query);
        layout.addView(submittedQuery);
    }

    private void addTitle(String text, int id) {
        TextView title = new TextView(this);
        title.setId(id);
        title.setText(text);
        title.setTextSize(22);
        layout.addView(title);
    }

    private void addButton(String label, int id, Runnable action) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        layout.addView(button);
    }
}
