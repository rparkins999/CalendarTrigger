# CalendarTrigger

Trigger actions on your Android device based on calendar events

This program is a generalisation of RemiNV/CalendarMute.

It is open source, free, and does not display adverts or pester you for a donation. This will never change as long as I am maintaining it. If you want to report a problem, please enable logging and provide a log file.

This is the screen displayed when you start up the UI
![CalendarTrigger](./assets/StartScreen.png)

This is the screen displayed when you select Debugging from the menu
![CalendarTrigger](./assets/DebuggingScreen.png)

CalendarTrigger supports classes of events. Events can be classified by the calendar which they are in, the presence of a specified string in the event name or the event location or the event description, whether the event is busy, whether the event is recurrent, whether the user is the event's organiser, whether the event is public or private, whether the event has attendees, or any combination of these conditions.

This is the screen displayed when you select NEW EVENT CLASS
![CalendarTrigger](./assets/NewClassScreen.png)

This is the screen displayed after creating a new class or if you select a class to be edited from the menu
![CalendarTrigger](./assets/ExampleScreen.png)

This is the screen to define the conditions for an event to be in a class
![CalendarTrigger](./assets/DefineClassScreen.png)

At a fixed interval (possibly zero) before the start of an event, CalendarTrigger can set the ringer to mute or vibrate or set Do Not Disturb mode on Android versions which support it, or show a notification and optionally play a sound, or any combination of these actions. If the event start action does not change the ringer state or play a sound, no notification will be shown. I may add other actions in the future. Event start actions can be delayed until the device is in a particular orientation or being charged by a particular type of charger or not being charged at all. This can be useful to set a sleep mode at night. If the event start action mutes the audio, any sound played will of course not be audible. An event can be in more than one class, so it is possible to play an audio reminder a few minutes before the start of an event and then mute the audio.

This is the screen to define when CalendarTrigger does event start actions
![CalendarTrigger](./assets/StartConditionScreen.png)

This is the screen to define what event start actions CalendarTrigger does
![CalendarTrigger](./assets/StartActionScreen.png)

At a fixed interval (possibly zero) after the end of an event, CalendarTrigger can restore the original ringer state, or show a notification and optionally play a sound. If the event end action does not change the ringer state or play a sound, no notification will be shown. Again I may add other actions in the future. Event end actions can be delayed until the device has moved by a certain distance (if it has a location sensor) or until the person holding the device has taken a certain number of steps (if it has a step counter) or until the device is in a particular orientation. This can be useful if you don't know exactly when an event will end, and you want to unmute the ringer when you leave the room or leave the building or pick the device up.

The duration of an event should be greater than zero: if it is zero, Android may awaken CalendarTrigger a short times before or after the start time of the event, and CalendarTrigger cannot tell whether the event has been dealt with or not.

This is the screen to define when CalendarTrigger does event end actions: the step counter option is disabled because the emulator on which these screen shots were generated doesn't emulate a step counter
![CalendarTrigger](./assets/EndConditionScreen.png)

This is the screen to define what event end actions CalendarTrigger does
![CalendarTrigger](./assets/EndActionScreen.png)

If events which set ringer modes overlap, the "quietest" one wins. If an event end action would restore the previous ringer state, and the user has in the meantime set a "quieter" state, the user's set state wins. The quietness order is as in the selection list for event start actions.

CalendarTrigger also has immediate events, useful if you walk into a "quiet" building and want to mute your ringer until you leave.

CalendarTrigger scans all of your calendars once for each event class every time anything happens which might cause it to take some action. This will not interfere with other applications because it happens in the background, but it does need the phone to wake up and use battery power, so you should not define too many event classes. Some Calendar Providers seem to generate many PROVIDER_CHANGED broadcasts even when nothing has actually changed, but CalendarTrigger needs to wake up for these broadcasts to determine whether in fact anything _has_ changed, so I can't do anything about this. You could try complaining to your phone manufacturer....

The UI is available in English and French: the French version could probably be improved as I am not a native speaker.

Help with the French translations would be welcome, as would UI translations for other languages.

## Help information
CalendarTrigger uses the convention that a long press on a user interface object (such as a button or a checkbox) will pop up some information (usually in a toast) explaining what it does. If an option is disabled because CalendarTrigger does not have the permissions it needs to do that function, a long press will explain which permission is needed to enable it. If an option is disabled because your device's operating system or hardware does not support it, a long press will say so.

This is a Screen showing a help information popup
![CalendarTrigger](./assets/StartHelpScreen.png)

If you want to report a bug, please provide a log file and a settings file

Some more complicated behaviours are described in this README file.

## Next Location feature

Some satnav systems can connect via Bluetooth to your phone, read your contact list, and navigate to the address of a selected contact. Unfortunately satnav
systems are a bit picky about address formats, and some can't decode the
unstructured string address of the contact. If you have a contacts manager which allows you to put in the address in separate fields for street address,
city, postcode, and country, you will get better results.

It would be nice if the satnav could navigate to the address of the next appointment in your calendar too, but there isn't a Bluetooth protocol for it to read your calendar. The next location feature in CalendarTrigger, which can be enabled from the debugging screen, attempts to work around this. It creates a virtual contact called !NextEventLocation (the ! makes it appear at the top of the list) and arranges for its address to always be the location of the next event in your calendar which has a location. The Location field in a calendar event is an unstructured string: CalendarTrigger does its best to decode this into its component parts. You can help it by using standard format addresses:

_20 Dean's Yard, London SW1P 3PA, England_  

or  

_Westminster Abbey, 20, Dean's Yard, London, SW1P 3PA, England_  

should work. It tries to handle neighbourhood names, but doesn't always succeed unless it can find a street number and a postcode and a country name as well:..

_Westminster Abbey, 20 Dean's Yard, City of Westminster, London SW1P 3PA, England_  

should work. If you're in Europe,  

_6 parvis Notre-Dame - Place John Paul II, F-75004 Paris_  
or..
_Groenplaats 21, 2000 Antwerpen, Belgium_..

should work. In the USA  

_1600 Pennsylvania Avenue NW, Washington, DC 20500, USA_  

should work as well. It knows the state names and abbreviations for the USA, so you can leave out the USA for American addresses (as Americans usually do): the format  

_1600 Pennsylvania Avenue NW, Washington 20500, District of Columbia_  

is also accepted.

If you leave out the country name for other countries, CalendarTrigger will assume that it is the country in which the phone currently is, as determined by the Mobile Country code of the cellular network to which it is currently connected, if any.

Flat or apartment numbers or rooms or PO boxes within a building will probably confuse it, because it can't tell "Apartment 17" from a street address in those countries where it's normal to put the house number after the street name.

Calendar Trigger will ignore anything in () or [] or {} or <>, so  

_(West Wing), 1600 Pennsylvania Avenue NW, Washington 20500, DC_  

will work: this is the best way of attaching sub-building information or company names to an address. Tags used to identify event classes can also be hidden from the address decoder in this way, for example  

_Shakespeare’s Globe, 21 New Globe Walk, London SE1 9DT, England {mute inside}_  

where presumably you have an event class which includes events whose location contains {mute inside}.

If the location of the event is of the form..
_@label firstname lastname_..
where _label_ is empty or _HOME_ or _WORK_ or_OTHER_ or a string matching the _LABEL_ of a _CUSTOM_ type address, CalendarTrigger uses the first address of the corresponding type (or any type if _label_ is empty) that it finds in any contact matching _firstname_ and _lastname_. This enables you to specify an event at a contact's address without having to retype the address. It doesn't parse the address, so the contact's address in the phone needs to be understandable to your satnav. Note that this is an event location format specific to CalendarTrigger: other calendar tools are unlikely to understand it.

## Signing and saving settings
Newer versions of Android do not allow you to install unsigned applications. The `.apk` file in the git release is signed (with my signing key) as is the apk file dowloadable from [fdroid](https://f-droid.org) (with their signing key). Naturally neither I nor fdroid are willing to publish our signing keys, so if you build your own version you will need to to sign it with your own signing key. The `app/build.gradle` file expects a `keystore.properties` in the project root directory, which you will need to fill with the details of your own signing key. You can find how to create it [here](https://developer.android.com/studio/publish/app-signing.html).

Having multiple signing keys causes problems if you have previously installed one version and want to install a newer version of the same application signed with a different key: Android does not allow this, and you have to uninstall the old application before installing the new one This deletes the application's data, which means for CalendarTrigger that you lose all its settings including all of your class definitions.

If Android will still not install a new version even after uninstalling the old one, this may be because the old `.apk` file is still present, which confuses the installer. Finding the old `.apk` file and deleting it should help.

In order save the applications's data, CalendarTrigger now allows you to save its settings to a (fixed) file or to replace the current settings by those from the file: there are buttons to do these actions in the Debugging screen. This can be used to save the settings before uninstalling, or to transfer your settings to a different device.

## Permissions
CalendarTrigger uses the following permissions:-

READ_CALENDAR
This is needed for CalendarTrigger to work at all: if this permission is denied, the User Interface will appear to work, but attempts to read the calendar will return nothing, so it will not do anything in response to calendar events.

READ_PHONE_STATE
This is needed to enable CalendarTrigger to avoid muting the audio during a call: if this permission is denied and an event calls for the audio to be muted, it may mute the audio even if a call is in progress. If it notices that this permission was previously granted but has been removed, it will display a notification (only once each time the permission state changes).

REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
This is needed to prevent Android from shutting down CalendarTrigger's background server. In some versions of Android, you need to explicitly whitelist CalendarTrigger somewhere in the settings page (unfortunately different versions do this in different places) in order to protect it from battery optimisation: other versions will ask you before applying battery optimisation to an application or permitting an application to have this permission. If this permission is denied and CalendarTrigger's background server is shut down unexpectedly, it may fail to respond correctly to the start or the end of an event.

WAKE_LOCK
This is needed to enable CalendarTrigger to stay awake while waiting for certain sensors to initialise: currently this permission is always granted. If some future Android version allows it to be denied, CalendarTrigger may crash or otherwise not work properly.

MODIFY_AUDIO_SETTINGS
This is needed to enable CalendarTrigger to mute the ringer: currently this permission is always granted. If some future Android version allows it to be denied, CalendarTrigger may crash or otherwise not work properly.

ACCESS_NOTIFICATION_POLICY
This is needed to enable CalendarTrigger to set and clear Do Not Disturb mode on those versions of Android which support it: if this permission is denied, it will revert to the behaviour on earlier versions of Android which only allow it to set the ringer to vibrate or silent, and the Do Not Disturb options on the Event Start Action screen will be disabled.

READ_CONTACTS
WRITE_CONTACTS
These are needed to make the Next Location feature work. CalendarTrigger only reads and write its own !NextEventLocation contact and does not read or write any other contacts (it is open source so you can check this). If this permission is denied, the Next Location feature is not available and the checkbox for it is disabled on the debugging screen: CalendarTrigger will otherwise work normally.

WRITE_EXTERNAL_STORAGE
This is needed to enable CalendarTrigger to write a log file or a settings file. If you never enable logging or save settings, it isn't needed; if this permission is denied, logging cannot be enabled and settings cannot be saved. If you enable logging, you need to clear the log file from time to time, otehrwise it will fill up memory.

ACCESS_FINE_LOCATION
This is needed to enable CalendarTrigger to detect when the device has moved by a sufficient distance after the end of an event: if this permission is denied, the option is disabled, but CalendarTrigger will otherwise work normally.

#### What can I legally do with this app ?
This application is released under the GNU GPL v3 or later, do make sure you abide by the license terms when using it.
Read the license terms for more details, but to make it very (too) simple: you can do everything you want with the application, as long as you provide your source code with any version you release, and release it under the same license.
