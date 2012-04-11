HrMIDI : HadronPlugin {
	var responder, deviceIndex = 0, channelIndex = 0, midiA = 1,
	spec, value = 0,
	midiType = \cc,  // for later
	prOutbus;

	var initButton, deviceMenu, channelMenu, midiAName, midiAEdit, specEdit, valueDisplay,
	modControl;

	*initClass {
		this.addHadronPlugin;
		StartUp.add {
			// future, add more midi types (note on/off, PB, etc.)
			// nil means, no midiA filtering
			Library.put('HrMIDI', \typeStrings, \cc, "Control");
		};
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		^super.new(argParentApp, "HrMIDI", argIdent, argUniqueID, argExtraArgs, Rect((Window.screenBounds.width - 330).rand, (Window.screenBounds.height - 185).rand, 330, 185), 0, 1, argCanvasXY).init
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

		midiAName = StaticText(window, Rect(5, 55, 150, 20))
		.align_(\center).background_(Color(0.88, 1.0, 1.0))
		.string_(Library.at('HrMIDI', \typeStrings, midiType));
		midiAEdit = NumberBox(window, Rect(160, 55, 60, 20))
		.value_(midiA)
		.action_({ |view|
			midiA = view.value.asInteger;
			if(Library.at('HrMIDI', \initCondition).test) { this.updateResponder };
		});

		specEdit = HrSpecEditor(window, Rect(5, 80, 320, 50), labelPrefix: "out")
		.action_({ |view|
			var oldspec = spec;
			spec = view.value;
			synthInstance.set(\value, spec.map(oldspec.unmap(value)));
			valueDisplay.spec_(spec).value_(value);
		});

		valueDisplay = HrEZSlider(window, Rect(5, 135, 320, 20), "value", spec)
		.enabled_(false);

		modControl = HadronModTargetControl(window, Rect(5, 160, 320, 20), parentApp, this);
		modControl.addDependant(this);

		initThread = Routine({
			Library.at('HrMIDI', \initCondition).wait;
			initButton.value_(2).enabled_(false);
			// ought to modularize these. later...
			deviceMenu.items_(["None"] ++ MIDIClient.sources.collect(_.name))
			.enabled_(true);
			channelMenu.items_(["None"] ++ (1..16).collect(_.asString) ++ ["omni"])
			.enabled_(true);
			this.makeSynth;
		}).play(AppClock);

		saveGets = [
			{ spec },
			{ value },
			{ midiType },
			{ midiA },
			{ channelIndex },
			{ MIDIClient.sources[deviceIndex - 1].tryPerform(\name) },
			{ modControl.getSaveValues }
		];

		saveSets = [
			{ |argg|
				spec = HrControlSpec.newFrom(argg);
				defer {
					valueDisplay.spec = spec;
					specEdit.value = spec;
				};
			},
			{ |argg|
				value = argg;
				defer { valueDisplay.value = value };
			},
			nil,  // later...
			{ |argg|
				midiA = argg;
				defer { midiAEdit.value = midiA };   // will updateResponder later
			},
			{ |argg|
				channelIndex = argg;  // gui update after MIDI init, below
			},
			{ |argg|
				{
					Library.at('HrMIDI', \initCondition).wait;
					0.05.wait;  // ensure other init happens first
					deviceIndex = MIDIClient.sources.detectIndex { |endpt|
						endpt.name == argg
					};
					channelMenu.value = channelIndex;
					if(deviceIndex.notNil) {
						deviceIndex = deviceIndex + 1;
						deviceMenu.value = deviceIndex;
						this.updateResponder;
					};
				}.fork(AppClock);
			},
			{ |argg| modControl.putSaveValues(argg) }
		];
	}

	updateResponder {
		if(deviceIndex == 0 or: { channelIndex == 0 }) {
			responder.remove;
			responder = nil;
		} {
			if(responder.isNil) {
				responder = CCResponder(
					{ |src, chan, num, val|
						value = spec.map(val / 127.0);
						synthInstance.set(\value, value);
						modControl.updateMappedGui(value);
						defer { valueDisplay.value = value };
					},
					MIDIClient.sources[deviceIndex - 1].uid,
					if(channelIndex < 17) { channelIndex - 1 } { nil },
					midiA
				)
			} {
				responder.matchEvent = MIDIEvent(nil, 
					MIDIClient.sources[deviceIndex - 1].uid,
					if(channelIndex < 17) { channelIndex - 1 } { nil },
					midiA
				);
			};
		};
	}

	synthArgs {
		^[\outBus0, outBusses[0], \prOutbus, prOutbus, \value, value]
	}

	makeSynthDef {
		if(SynthDescLib.at('HrMIDI').isNil) {
			SynthDef('HrMIDI', { |outBus0, prOutbus, value, lag = 0.08|
				var trig = HPZ1.kr(value).abs > 0,
				lagger = EnvGen.kr(Env([value, value], [lag]), trig);
				Out.kr(prOutbus, lagger);
				Out.ar(outBus0, K2A.ar(lagger));
			}).add;
		};
	}

	defName { ^this.class.name }

	update { |obj, what, argument, oldplug, oldparam|
		if(#[currentSelPlugin, currentSelParam].includes(what)) {
			if(argument.notNil) {
				modControl.unmap(oldplug, oldparam);
				modControl.map(prOutbus);
			} {
				modControl.unmap(oldplug, oldparam);
			};
		};
	}

	updateBusConnections {
		synthInstance.set(\outBus0, outBusses[0], \prOutbus, prOutbus);
	}

	cleanUp {
		prOutbus.free;
		responder.remove;
		modControl.unmap.removeDependant(this).remove;
	}

	notifyPlugKill { |argPlug|
		modControl.plugRemoved(argPlug);
	}
	
	notifyPlugAdd { |argPlug|
		modControl.plugAdded;
	}

	notifyIdentChanged {
		modControl.do { |ctl| ctl.refreshAppMenu };
	}

	wakeFromLoad {
		{
			Library.at('HrMIDI', \initCondition).wait;
			modControl.doWakeFromLoad;
		}.fork(AppClock);
	}
}