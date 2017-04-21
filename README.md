CalendarTrigger
---------------

Trigger actions on your Android device based on calendar events

This program is a generalisation of RemiNV/CalendarMute.

It can trigger different actions on different types of calendar event, and handles overlapping events wanting different ringer states in a sensible way (the "quietest" state wins). Also the start and end actions for an event can be delayed until the device is in some particular state. This requires a preferences format incompatible with the original calendar mute, hence the need for a new repo.

This version works, and you are welcome to play with it. It is open source, free, and does not display adverts or pester you for a donation. This will never change as long as I am maintaining it. If you want to report a problem, please enable logging and provide a log file.

This version supports classes of events. The only event start actions currently available are to set the ringer to mute or vibrate or to set Do Not Disturb mode on Android versions which support it, and optionally to show a notification if it changes the ringer state: I intend to add others. Event start actions can be delayed until the device is in a particular orientation or being charged by a particular type of charger or not being charged at all: this is
part of a plan to detect when the device's owner is asleep, and mute the ringer
until the owner wakes up and picks up the device. The event end actions currently supported are to do nothing or to restore the original ringer state, and optionally to show a notification if it changes the ringer state: again I intend to add others. The event end action can be delayed by a set time or until the device has moved by a certain distance (if it has a location sensor) or until the person holding the device has taken a certain number of steps (if it has a step counter). This can be useful if you don't know exactly when an event will end, and you want to unmute the ringer when you leave the room or leave the building. This version also has immediate events, useful if you walk into a "quiet" building and want to mute your ringer until you leave.

If an event is in more than one class, the actions for all the classes which contain it are triggered.

The UI is available in English and French: the French version could probably be improved as I am not a native speaker.

Help with the French translations would be welcome, as would UI translations for other languages.

#### What can I legally do with this app ?
This application is released under the GNU GPL v3 or later, do make sure you abide by the license terms when using it.
Read the license terms for more details, but to make it very (too) simple: you can do everything you want with the application, as long as you provide your source code with any version you release, and 
release it under the same license.
