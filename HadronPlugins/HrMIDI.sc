HrMIDI : HadronPlugin {
	var responder, deviceIndex = 0, channelIndex = 0, ctlNum = 1,
	spec,
	prOutbus;

	var initButton, deviceMenu, channelMenu, specEdit, valueDisplay;

	*initClass { this.addHadronPlugin }

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		^super.new(argParentApp, "HrMIDI", argIdent, argUniqueID, argExtraArgs, Rect((Window.screenBounds.width - 330).rand, (Window.screenBounds.height - 150).rand, 330, 150), 0, 1, argCanvasXY).init
	}

	init {
		var initThread;

		prOutbus = Bus.control(Server.default, 1);
		spec = HrControlSpec(0, 1);

		initButton = Button(window, Rect(5, 5, 180, 20))
		.states_([
			["Connect MIDI"],
			["Connecting...", Color.black, Color(1.0, 0.6, 0.6)],
			["MIDI connected", Color.black, Color(0.8, 1.0, 0.8)]
		]);

		if(Library.at('HrMIDI', \initCondition).isNil) {
			Library.put('HrMIDI', \initCondition, Condition(false));
		};
		if(MIDIClient.initialized) {
			initButton.value_(2).enabled_(false);
			// this triggers the final init actions
			Library.at('HrMIDI', \initCondition).test_(true).signal;
		} {
			initButton.action = {
				{
					0.02.wait;  // time for button to update
					try {
						MIDIClient.init;
						MIDIIn.connectAll;
						Library.at('HrMIDI', \initCondition).test_(true).signal;
					} { |err|
						defer {
							parentApp.displayStatus("MIDI init error: " ++ err.errorString, -1);
							initButton.value = 0;
						};
					};
				}.fork(AppClock);
			};
		};

		deviceMenu = PopUpMenu(window, Rect(5, 30, 250, 20))
		.enabled_(false)
		.action_({ |view|
			deviceIndex = view.value;
			this.updateResponder;
		});
		channelMenu = PopUpMenu(window, Rect(260, 30, 60, 20))
		.enabled_(false)
		.action_({ |view|
			channelIndex = view.value;
			this.updateResponder;
		});

		valueDisplay = HrEZSlider(window, Rect(5, 55, 320, 20), "value", spec)
		.enabled_(false);

		initThread = Routine({
			Library.at('HrMIDI', \initCondition).wait;
			initButton.value_(2).enabled_(false);
			// ought to modularize these. later...
			deviceMenu.items_(["None"] ++ MIDIClient.sources.collect(_.name))
			.enabled_(true);
			channelMenu.items_(["None"] ++ (1..16).collect(_.asString) ++ ["omni"])
			.enabled_(true);
			
			// this.makeResponder;
		}).play(AppClock);
	}

	updateResponder {
		if(deviceIndex == 0 or: { channelIndex == 0 }) {
			responder.remove;
			responder = nil;
		} {
			if(responder.isNil) {
				responder = CCResponder(
					{ |src, chan, num, value|
						value = spec.map(value / 127.0);
						synthInstance.set(\value, value);
						defer { valueDisplay.value = value };
					},
					MIDIClient.sources[deviceIndex - 1].uid,
					if(channelIndex < 17) { channelIndex - 1 } { nil },
					ctlNum
				)
			} {
				responder.matchEvent = MIDIEvent(nil, 
					MIDIClient.sources[deviceIndex - 1].uid,
					if(channelIndex < 17) { channelIndex - 1 } { nil },
					ctlNum
				);
			};
		};
	}

	cleanUp {
		prOutbus.free;
		responder.remove;
	}
}