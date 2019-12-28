package com.eightbitlab.blurview_sample;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import butterknife.BindView;

public class ListFragment extends BaseFragment {
    @BindView(R.id.recyclerView) RecyclerView recyclerView;

    @Override
    int getLayoutId() {
        return R.layout.fragment_list;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        init();
    }

    private void init() {
        recyclerView.setAdapter(new ExampleListAdapter(getContext()));
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }
}
