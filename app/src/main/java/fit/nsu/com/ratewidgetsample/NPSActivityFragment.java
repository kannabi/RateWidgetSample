package fit.nsu.com.ratewidgetsample;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;


public class NPSActivityFragment extends Fragment {

    private Button mRateButton;
    private RateWidget mRateWidget;

    public NPSActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_nps, container, false);

        return rootView;
    }
}
