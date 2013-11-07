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
	protected CheckBox chkOnlyBusy;
	protected EditText txtDelay;
	
	private RadioGroup.OnCheckedChangeListener radioGroupActionCheckedChangedListener = new RadioGroup.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(RadioGroup group, int checkedId) {
			// Sauvegarde de la nouvelle valeur
			Activity a = getActivity();
			
			switch(checkedId) {
			case R.id.radioSilencieux:
				PreferencesManager.setActionSonnerie(a, PreferencesManager.PREF_ACTION_SONNERIE_SILENCIEUX);
				break;
			case R.id.radioVibreur:
				PreferencesManager.setActionSonnerie(a, PreferencesManager.PREF_ACTION_SONNERIE_VIBREUR);
				break;
			case R.id.radioRienFaire:
			default:
				PreferencesManager.setActionSonnerie(a, PreferencesManager.PREF_ACTION_SONNERIE_RIEN);
				break;
			}
			
			// Suppression du mode assigné actuel pour le remettre à jour
			PreferencesManager.setLastSetRingerMode(a, PreferencesManager.PREF_LAST_SET_RINGER_MODE_NO_MODE);
			
			// Lancement du service pour update
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
			PreferencesManager.setOnlyBusy(getActivity(), isChecked);
		}
	};
	
	private CompoundButton.OnCheckedChangeListener chkDelayActivatedChangeListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			PreferencesManager.setDelayActived(getActivity(), isChecked);
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
				int delay = Integer.parseInt(s.toString());
				
				PreferencesManager.setDelay(getActivity(), delay);
			}
			catch(NumberFormatException e) {
				txtDelay.setText(PreferencesManager.PREF_DELAY_DEFAULT);
			}
			
		}

		@Override
		public void afterTextChanged(Editable s) { }
		
	};
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.layout_actions, container, false);
		
		radioGroupAction = (RadioGroup) res.findViewById(R.id.radioGroupActionSonnerie); 
		chkRestaurer = (CheckBox) res.findViewById(R.id.chkRestaurerEtat);
		chkNotif = (CheckBox) res.findViewById(R.id.chkAfficherNotif);
		chkOnlyBusy = (CheckBox) res.findViewById(R.id.chkSeulementOccupe);
		chkDelayActivated = (CheckBox) res.findViewById(R.id.chkDelayActivated);
		txtDelay = (EditText) res.findViewById(R.id.txtDelay);
		
		restaurerValeurs();
		
		// Listeners
		radioGroupAction.setOnCheckedChangeListener(radioGroupActionCheckedChangedListener);
		chkRestaurer.setOnCheckedChangeListener(chkRestaurerCheckedChangeListener);
		chkNotif.setOnCheckedChangeListener(chkAfficherNotifCheckedChangeListener);
		chkDelayActivated.setOnCheckedChangeListener(chkDelayActivatedChangeListener);
		chkOnlyBusy.setOnCheckedChangeListener(chkOnlyBusyCheckedChangeListener);
		txtDelay.addTextChangedListener(txtDelayChangeListener);
		
		return res;
	}
	
	public void restaurerValeurs() {
		
		Activity a = getActivity();
		
		// Radiogroup
		int actionSonnerie = PreferencesManager.getActionSonnerie(a);
		
		switch(actionSonnerie) {
		case PreferencesManager.PREF_ACTION_SONNERIE_SILENCIEUX:
			radioGroupAction.check(R.id.radioSilencieux);
			break;
			
		case PreferencesManager.PREF_ACTION_SONNERIE_VIBREUR:
			radioGroupAction.check(R.id.radioVibreur);
			break;
		case PreferencesManager.PREF_ACTION_SONNERIE_RIEN:
		default:
			radioGroupAction.check(R.id.radioRienFaire);
			break;
		}
		
		chkRestaurer.setChecked(PreferencesManager.getRestaurerEtat(a));
		
		chkNotif.setChecked(PreferencesManager.getAfficherNotif(a));
		
		chkDelayActivated.setChecked(PreferencesManager.getDelayActivated(a));
		
		chkOnlyBusy.setChecked(PreferencesManager.getOnlyBusy(a));
		
		txtDelay.setText(PreferencesManager.getDelay(a));
	}
}
