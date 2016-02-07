package uk.co.yahoo.p1rpp.calendartrigger.views;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import android.view.LayoutInflater;
import android.view.accessibility.AccessibilityNodeInfo;

public class CalendarListItem extends RelativeLayout implements Checkable {

	private CheckBox checkBox;
	
	public CalendarListItem(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.customview_calendar_list_item, this, true);
		
		// Get the checkbox
		checkBox = (CheckBox) findViewById(R.id.chk_calendar);
	}
	
	public void setTitle(String title) {
		((TextView) findViewById(R.id.lbl_calendar_name)).setText(title);
	}
	
	public void setTitle(int titleResId) {
		((TextView) findViewById(R.id.lbl_calendar_name)).setText(titleResId);
	}
	
	public void setSubTitle(String title) {
		((TextView) findViewById(R.id.lbl_calendar_details)).setText(title);
	}
	
	public void setSubTitle(int titleResId) {
		((TextView) findViewById(R.id.lbl_calendar_details)).setText(titleResId);
	}

	@Override
	public boolean isChecked() {
		return checkBox.isChecked();
	}

	@Override
	public void setChecked(boolean checked) {
		checkBox.setChecked(checked);
	}

	@Override
	public void toggle() {
		checkBox.toggle();
	}
	
	public void setCheckboxOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener) {
		checkBox.setOnCheckedChangeListener(listener);
	}
	
	@Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.setClassName(CalendarListItem.class.getName());
        info.setCheckable(true);
        info.setChecked(isChecked());
    }

}
