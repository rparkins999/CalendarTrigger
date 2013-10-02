package com.PKH.calendarmute.views;

import com.PKH.calendarmute.R;
import com.PKH.calendarmute.models.Calendar;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;

public class CalendarAdapter extends ArrayAdapter<Calendar> {

	private Context context;
	private ItemCheckedChangedListener changeListener;
	
	public static abstract class ItemCheckedChangedListener implements CompoundButton.OnCheckedChangeListener {
		public abstract void onItemCheckedChanged();
		
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
			onItemCheckedChanged();
		}
	};
	
	/**
	 * Définit le listener au changement d'état d'un item cochable. Doit être appelé *avant* la création de chacune
	 * des vues pour s'appliquer à celles-ci
	 * @param listener Listener à appliquer
	 */
	public void setItemCheckedChangedListener(ItemCheckedChangedListener listener) {
		changeListener = listener;
	}
	
	public CalendarAdapter(Context context, Calendar[] objects) {
		super(context, -1, objects);
		
		this.context = context;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		View targetView;
		
		if(convertView != null)
			targetView = convertView;
		else {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			targetView = inflater.inflate(R.layout.list_item_agenda, parent, false);
		}
		
		// Population de la vue
		Calendar cal = this.getItem(position);
		
		CalendarListItem view = (CalendarListItem) targetView;
		view.setTitle(cal.getDisplayName());
		int syncInfo = cal.isSynced() ? R.string.synchronise : R.string.non_synchronise;
		view.setSubTitle(syncInfo);
		view.setChecked(cal.isChecked());
		
		if(changeListener != null)
			view.setCheckboxOnCheckedChangeListener(changeListener);

		return targetView;
	}
	
	@Override
	public boolean hasStableIds() {
		return true;
	}
	
	@Override
	public long getItemId(int position) {
		return getItem(position).getId();
	}
	
}
