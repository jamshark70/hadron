HrCtlMod : HrSimpleModulator {
	classvar <>defaultPollRate = 4;
	var evalButton, watcher, isMapped = false, numChannels = 1, replyID;
	var pollRate, pollRateView;

	*initClass
	{
		this.addHadronPlugin;
	}
	*height { |extraArgs| ^175 + (25 * this.shouldWatch(extraArgs).binaryValue) }

	// default is to watch
	// it is really sucky to have to make this a class method
	// but I need to use it to determine window bounds,
	// and that's done at class-method level
	*shouldWatch { |argExtraArgs|
		^argExtraArgs.size < 2 or: {
			argExtraArgs[1] != "0" and: { argExtraArgs[1] != "false" }
		}
	}
	shouldWatch { ^this.class.shouldWatch(extraArgs) }

	modulatesOthers { ^true }
	// copy and paste programming...
	init
	{
		window.background_(Color.gray(0.7));
		if(extraArgs.size >= 1) {
			// because "a non-number string".asInteger is 0
			numChannels = max(1, extraArgs[0].asInteger);
			if(extraArgs[2].size > 0 and: { extraArgs[2].asFloat > 0 }) {
				pollRate = extraArgs[2].asFloat;
			} {
				pollRate = defaultPollRate;
			};
		} {
			pollRate = defaultPollRate;  // and numChannels uses the default from var declaration
		};
		prOutBus = Bus.control(Server.default, numChannels);
		helpString = "Write a modulation function, maybe using an audio input.";
		StaticText(window, Rect(10, 20, 150, 20)).string_("Modulation function");

		postOpFunc = {|sig| (sig * 0.5) + 0.5; };

		postOpText = TextView(window, Rect(10, 20, 430, 95))
		.string_("{ |sig| SinOsc.kr(1, 0, 0.5, 0.5) }");

		evalButton = Button(window, Rect(10, 120, 80, 20))
		.states_([["Evaluate"]])
		.action_({
			postOpFunc = postOpText.string.interpret;
			this.makeSynth;
		});

		modControl = HadronModTargetControl(window, Rect(10, 150, 430, 20), parentApp, this);
		modControl.addDependant(this);

		startButton = Button(window, Rect(100, 120, 80, 20)).states_([["Start"],["Stop"]])
		.value_(binaryValue(modControl.currentSelPlugin.notNil and: {
			modControl.currentSelParam.notNil
		}))
		.action_
		({|btn|
			if(btn.value == 1) {
				modControl.map(prOutBus);
				isMapped = true;
				synthInstance.set(\pollRate, pollRate * (watcher.notNil.binaryValue));
			} {
				modControl.unmap;
				isMapped = false;
				synthInstance.set(\pollRate, 0);
			};
		});

		if(this.shouldWatch) {
			pollRateView = HrEZSlider(window, Rect(10, 175, 430, 20),
				"update rate", [1, 25], { |view|
					pollRate = view.value;
					if(synthInstance.notNil) {
						synthInstance.set(\pollRate, pollRate * (watcher.notNil.binaryValue));
					};
				}, pollRate, labelWidth: 100, numberWidth: 45
			);
		};

		this.makeSynth;

		saveGets =
			[
				{ postOpText.string; },
				{ modControl.getSaveValues; },
				{ startButton.value; },
				{ pollRate }
			];

		saveSets =
			[
				{ |argg| postOpText.string_(argg); evalButton.doAction },
				{ |argg| modControl.putSaveValues(argg); },
				{ |argg| startButton.valueAction_(argg); },
				{ |argg|
					if(argg.notNil) {
						pollRate = argg;
						pollRateView.tryPerform(\valueAction_, argg);
					}
				}
			];

		if(this.shouldWatch) {
			// ideally the copy and paste here is not so hot,
			// but the optimization is worth it
			if(numChannels == 1) {
				watcher = OSCresponderNode(Server.default.addr, '/modValue', { |time, resp, msg|
					if(msg[2] == replyID) {
						modControl.updateMappedGui(msg[3]);
					}
				}).add;
			} {
				watcher = OSCresponderNode(Server.default.addr, '/modValue', { |time, resp, msg|
					if(msg[2] == replyID) {
						modControl.updateMappedGui(msg[3..]);
					}
				}).add;
			};
		};
	}

	releaseSynth { synthInstance.free; synthInstance = nil; }

	makeSynth { |newSynthDef(true)|
		// it's a little bit dumb that I have to do this, but
		// it's the only way to conditionally not execute something after try
		var shouldPlay = true;
		fork
		{
			if(newSynthDef) {
				try {
					this.makeSynthDef;
				} { |err|
					if(err.isKindOf(Exception)) {
						shouldPlay = false;
						err.reportError;
						defer { parentApp.displayStatus(err.errorString, -1) };
					};
				};
			};
			if(shouldPlay) {
				Server.default.sync;
				if(synthInstance.notNil) {
					synthInstance = Synth(this.class.name++uniqueID,
						this.synthArgs,
						target: synthInstance, addAction: \addReplace
					);
				} {
					synthInstance = Synth(this.class.name++uniqueID,
						this.synthArgs, target: group
					);
				};
			};
		};
	}
	synthArgs { ^[\inBus0, inBusses[0], \prOutBus, prOutBus,
		pollRate: pollRate * isMapped.binaryValue * (watcher.notNil.binaryValue)
	] }

	makeSynthDef {
		// this is kind of dumb
		// but the 32-bit int uniqueID is too big for single-float precision
		// so I need another ID
		replyID = UniqueID.next;
		SynthDef("HrCtlMod"++uniqueID, { |prOutBus, inBus0, pollRate = 0|
			var input = A2K.kr(InFeedback.ar(inBus0));
			input = postOpFunc.value(input);
			if(input.size > numChannels or: { input.rate != \control }) {
				// throw prevents the synthdef from being replaced
				Exception(
					"HrCtlMod result must be % channel%, control rate"
					.format(numChannels, if(numChannels > 1, "s", ""))
				).throw;
			} {
				input = input.asArray.wrapExtend(numChannels);
			};
			SendReply.kr(Impulse.kr(pollRate), '/modValue', input, replyID);
			Out.kr(prOutBus, input);
		}).add;
	}

	cleanUp
	{
		this.releaseSynth;
		modControl.removeDependant(this);
		watcher.remove;
		prOutBus.free;
	}

	update { |obj, what, argument, oldplug, oldparam|
		if(#[currentSelPlugin, currentSelParam].includes(what)) {
			if(argument.notNil) {
				modControl.unmap(oldplug, oldparam);
				isMapped = modControl.map(prOutBus);
				synthInstance.set(\pollRate,
					pollRate * isMapped.binaryValue * (watcher.notNil.binaryValue)
				);
				defer { startButton.value = isMapped.binaryValue };
			} {
				modControl.unmap(oldplug, oldparam);
				synthInstance.set(\pollRate, 0);
				isMapped = false;
				defer { startButton.value = 0 };
			};
		};
	}
}