package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.app.Fragment;
import android.media.AudioManager;
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

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.service.MuteService;

public class ActionsFragment extends Fragment {

	protected RadioGroup radioGroupAction;
	protected RadioGroup radioGroupLogging;
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
				PrefsManager.setActionSonnerie(a, AudioManager.RINGER_MODE_SILENT);
				break;
			case R.id.radioVibrate:
				PrefsManager.setActionSonnerie(a, AudioManager.RINGER_MODE_VIBRATE);
				break;
			case R.id.radioDoNothing:
				PrefsManager.setActionSonnerie(a, PrefsManager.RINGER_MODE_NONE);
				break;
			default:
			}
			
			// Remove current set mode to update it afterwards
			PrefsManager.setLastSetRingerMode(a, PrefsManager.RINGER_MODE_NONE);
			
			// Launch update service
			MuteService.startIfNecessary(a, "actions changed");
		}
	};

	private RadioGroup.OnCheckedChangeListener
		radioGroupLoggingCheckedChangedListener = new RadioGroup.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(RadioGroup group, int checkedId) {
			// Save new value
			Activity a = getActivity();

			switch(checkedId)
			{
				case R.id.radioLoggingOn:
					PrefsManager.setLoggingMode(a, true);
					break;
				case R.id.radioLoggingOff:
					PrefsManager.setLoggingMode(a, false);
					break;
				default:
			}
		}
	};

	private CompoundButton.OnCheckedChangeListener chkRestaurerCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			PrefsManager.setRestaurerEtat(getActivity(), isChecked);
		}
	};
	
	private CompoundButton.OnCheckedChangeListener chkAfficherNotifCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			PrefsManager.setAfficherNotif(getActivity(), isChecked);
		}
	};
	
	private CompoundButton.OnCheckedChangeListener chkOnlyBusyCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			
			Activity a = getActivity();
			
			PrefsManager.setOnlyBusy(a, isChecked);
			
			// Launch update service
			MuteService.startIfNecessary(a, "busy state changed");
		}
	};
	
	private CompoundButton.OnCheckedChangeListener chkDelayActivatedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			
			Activity a = getActivity();
			
			PrefsManager.setDelayActived(a, isChecked);
			
			// Launch update service
			MuteService.startIfNecessary(a, "chkDelayActivatedChangeListener");
		}
	};
	
	private CompoundButton.OnCheckedChangeListener chkEarlyActivatedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			
			Activity a = getActivity();
			
			PrefsManager.setEarlyActived(a, isChecked);
			
			// Launch update service
			MuteService.startIfNecessary(a, "chkEarlyActivatedChangeListener");
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
				
				PrefsManager.setDelay(a, delay);
				
				MuteService.startIfNecessary(a, "txtDelayChangeListener");
			}
			catch(NumberFormatException e) {
				txtDelay.setText(String.valueOf(PrefsManager.PREF_DELAY_DEFAULT));
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
				
				PrefsManager.setEarly(a, early);
				
				MuteService.startIfNecessary(a, "txtEarlyChangeListener");
			}
			catch(NumberFormatException e) {
				txtEarly.setText(String.valueOf(PrefsManager.PREF_DELAY_DEFAULT));
			}
		}

		@Override
		public void afterTextChanged(Editable s) { }
	};
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.layout_actions, container, false);

		radioGroupAction = (RadioGroup) res.findViewById(R.id.radioGroupRingerAction);
		radioGroupLogging = (RadioGroup) res.findViewById(R.id
														.radioGroupLogging);
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
		radioGroupLogging.setOnCheckedChangeListener(radioGroupLoggingCheckedChangedListener);
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
		int ringerAction = PrefsManager.getRingerAction(a);
		
		switch(ringerAction) {
		case AudioManager.RINGER_MODE_SILENT:
			radioGroupAction.check(R.id.radioSilent);
			break;
			
		case AudioManager.RINGER_MODE_VIBRATE:
			radioGroupAction.check(R.id.radioVibrate);
			break;
		case PrefsManager.RINGER_MODE_NONE:
		default:
			radioGroupAction.check(R.id.radioDoNothing);
			break;
		}
		
		chkRestaurer.setChecked(PrefsManager.getRestoreState(a));
		
		chkNotif.setChecked(PrefsManager.getShowNotif(a));
		
		chkDelayActivated.setChecked(PrefsManager.getDelayActivated(a));
		
		chkEarlyActivated.setChecked(PrefsManager.getEarlyActivated(a));
		
		chkOnlyBusy.setChecked(PrefsManager.getOnlyBusy(a));
		
		txtDelay.setText(String.valueOf(PrefsManager.getDelay(a)));
		
		txtEarly.setText(String.valueOf(PrefsManager.getDelay(a)));
	}
}
