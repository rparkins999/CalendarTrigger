package com.PKH.calendarmute.activites;

import com.PKH.calendarmute.PreferencesManager;
import com.PKH.calendarmute.R;
import com.PKH.calendarmute.service.MuteService;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;

public class ActionsFragment extends Fragment {

	protected RadioGroup radioGroupAction;
	protected CheckBox chkRestaurer;
	protected CheckBox chkNotif;
	protected CheckBox chkDelayActivated;
	protected CheckBox chkEarlyActivated;
	protected CheckBox chkOnlyBusy;
	protected EditText txtDelay;
	protected EditText txtEarly;
	
	private RadioGroup.OnCheckedChangeListener radioGroupActionCheckedChangedListener = new RadioGroup.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(RadioGroup group, int checkedId) {
			// Save new value
			Activity a = getActivity();
			
			switch(checkedId) {
			case R.id.radioSilent:
				PreferencesManager.setActionSonnerie(a, PreferencesManager.PREF_ACTION_RINGER_SILENT);
				break;
			case R.id.radioVibrate:
				PreferencesManager.setActionSonnerie(a, PreferencesManager.PREF_ACTION_RINGER_VIBRATE);
				break;
			case R.id.radioDoNothing:
			default:
				PreferencesManager.setActionSonnerie(a, PreferencesManager.PREF_ACTION_RINGER_NOTHING);
				break;
			}
			
			// Remove current set mode to update it afterwards
			PreferencesManager.setLastSetRingerMode(a, PreferencesManager.PREF_LAST_SET_RINGER_MODE_NO_MODE);
			
			// Launch update service
			MuteService.startIfNecessary(a);
		}
	};
	
	private CompoundButton.OnCheckedChangeListener chkRestaurerCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			PreferencesManager.setRestaurerEtat(getActivity(), isChecked);
		}
	};
	
	private CompoundButton.OnCheckedChangeListener chkAfficherNotifCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			PreferencesManager.setAfficherNotif(getActivity(), isChecked);
		}
	};
	
	private CompoundButton.OnCheckedChangeListener chkOnlyBusyCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			
			Activity a = getActivity();
			
			PreferencesManager.setOnlyBusy(a, isChecked);
			
			// Launch update service
			MuteService.startIfNecessary(a);
		}
	};
	
	private CompoundButton.OnCheckedChangeListener chkDelayActivatedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			
			Activity a = getActivity();
			
			PreferencesManager.setDelayActived(a, isChecked);
			
			// Launch update service
			MuteService.startIfNecessary(a);
		}
	};
	
	private CompoundButton.OnCheckedChangeListener chkEarlyActivatedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			
			Activity a = getActivity();
			
			PreferencesManager.setEarlyActived(a, isChecked);
			
			// Launch update service
			MuteService.startIfNecessary(a);
		}
	};
	
	private TextWatcher txtDelayChangeListener = new TextWatcher() {

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) { }

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			
			try {
				Activity a = getActivity();
				
				int delay = (s.length() == 0 ? 0 : Integer.parseInt(s.toString()));
				
				PreferencesManager.setDelay(a, delay);
				
				MuteService.startIfNecessary(a);
			}
			catch(NumberFormatException e) {
				txtDelay.setText(String.valueOf(PreferencesManager.PREF_DELAY_DEFAULT));
			}
		}

		@Override
		public void afterTextChanged(Editable s) { }
	};
	
	private TextWatcher txtEarlyChangeListener = new TextWatcher() {

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) { }

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			
			try {
				Activity a = getActivity();
				
				int early = (s.length() == 0 ? 0 : Integer.parseInt(s.toString()));
				
				PreferencesManager.setEarly(a, early);
				
				MuteService.startIfNecessary(a);
			}
			catch(NumberFormatException e) {
				txtEarly.setText(String.valueOf(PreferencesManager.PREF_DELAY_DEFAULT));
			}
		}

		@Override
		public void afterTextChanged(Editable s) { }
	};
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.layout_actions, container, false);
		
		radioGroupAction = (RadioGroup) res.findViewById(R.id.radioGroupRingerAction);
		chkRestaurer = (CheckBox) res.findViewById(R.id.chkRestoreState);
		chkNotif = (CheckBox) res.findViewById(R.id.chkShowNotif);
		chkOnlyBusy = (CheckBox) res.findViewById(R.id.chkOnlyBusy);
		chkDelayActivated = (CheckBox) res.findViewById(R.id.chkDelayActivated);
		chkEarlyActivated = (CheckBox) res.findViewById(R.id.chkEarlyActivated);
		txtDelay = (EditText) res.findViewById(R.id.txtDelay);
		txtEarly = (EditText) res.findViewById(R.id.txtEarly);
		
		restoreValues();
		
		// Listeners
		radioGroupAction.setOnCheckedChangeListener(radioGroupActionCheckedChangedListener);
		chkRestaurer.setOnCheckedChangeListener(chkRestaurerCheckedChangeListener);
		chkNotif.setOnCheckedChangeListener(chkAfficherNotifCheckedChangeListener);
		chkDelayActivated.setOnCheckedChangeListener(chkDelayActivatedChangeListener);
		chkEarlyActivated.setOnCheckedChangeListener(chkEarlyActivatedChangeListener);
		chkOnlyBusy.setOnCheckedChangeListener(chkOnlyBusyCheckedChangeListener);
		txtDelay.addTextChangedListener(txtDelayChangeListener);
		txtEarly.addTextChangedListener(txtEarlyChangeListener);
		
		return res;
	}
	
	public void restoreValues() {
		
		Activity a = getActivity();
		
		// Radiogroup
		int ringerAction = PreferencesManager.getRingerAction(a);
		
		switch(ringerAction) {
		case PreferencesManager.PREF_ACTION_RINGER_SILENT:
			radioGroupAction.check(R.id.radioSilent);
			break;
			
		case PreferencesManager.PREF_ACTION_RINGER_VIBRATE:
			radioGroupAction.check(R.id.radioVibrate);
			break;
		case PreferencesManager.PREF_ACTION_RINGER_NOTHING:
		default:
			radioGroupAction.check(R.id.radioDoNothing);
			break;
		}
		
		chkRestaurer.setChecked(PreferencesManager.getRestoreState(a));
		
		chkNotif.setChecked(PreferencesManager.getShowNotif(a));
		
		chkDelayActivated.setChecked(PreferencesManager.getDelayActivated(a));
		
		chkEarlyActivated.setChecked(PreferencesManager.getEarlyActivated(a));
		
		chkOnlyBusy.setChecked(PreferencesManager.getOnlyBusy(a));
		
		txtDelay.setText(String.valueOf(PreferencesManager.getDelay(a)));
		
		txtEarly.setText(String.valueOf(PreferencesManager.getDelay(a)));
	}
}
