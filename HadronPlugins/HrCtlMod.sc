HrCtlMod : HrSimpleModulator {
	classvar <>pollRate = 4;
	var evalButton, watcher, isMapped = false;

	*initClass
	{
		this.addHadronPlugin;
	}
	*height { ^175 }
	modulatesOthers { ^true }
	// copy and paste programming...
	init
	{
		window.background_(Color.gray(0.7));
		prOutBus = Bus.control(Server.default, 1);
		helpString = "Write a modulation function, maybe using an audio input.";
		StaticText(window, Rect(10, 20, 150, 20)).string_("Modulation function");

		postOpFunc = {|sig| (sig * 0.5) + 0.5; };

		// fix this
		postOpText = TextView(window, Rect(10, 20, 430, 95))
		.string_("{ |sig| SinOsc.kr(1, 0, 0.5, 0.5) }");
		// .action_({|txt|
		// 	postOpFunc = txt.value.interpret;
		// 	this.makeSynth;
		// });

		evalButton = Button(window, Rect(10, 120, 80, 20))
		.states_([["Evaluate"]])
		.action_({
			postOpFunc = postOpText.string.interpret;
			this.makeSynth;
		});

		modControl = HadronModTargetControl(window, Rect(10, 150, 430, 20), parentApp);
		modControl.addDependant(this);

		startButton = Button(window, Rect(100, 120, 80, 20)).states_([["Start"],["Stop"]])
		.value_(modControl.currentSelPlugin.notNil and: {
			modControl.currentSelParam.notNil
		})
		.action_
		({|btn|
			// synthInstance.run(btn.value == 1)
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

		this.makeSynth;

		saveGets =
			[
				{ postOpText.string; },
				{ modControl.getSaveValues; },
				{ startButton.value; }
			];

		saveSets =
			[
				{|argg| postOpText.string_(argg); evalButton.doAction },
				{|argg| modControl.putSaveValues(argg); },
				{|argg| startButton.valueAction_(argg); }
			];

		if(this.shouldWatch) {
			watcher = OSCresponderNode(Server.default.addr, '/modValue', { |time, resp, msg|
				if(msg[2] == uniqueID) {
					modControl.updateMappedGui(msg[3]);
				}
			}).add;
		};
	}

	// default is to watch
	shouldWatch {
		^extraArgs.size < 1 or: {
			extraArgs[0] != "0" and: { extraArgs[0] != "false" }
		}
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
		SynthDef("HrCtlMod"++uniqueID, { |prOutBus, inBus0, pollRate = 0|
			var input = A2K.kr(InFeedback.ar(inBus0));
			input = postOpFunc.value(input);
			if(input.size > 1 or: { input.rate != \control }) {
				// throw prevents the synthdef from being replaced
				Exception("HrCtlMod result must be one channel, control rate").throw;
			};
			SendReply.kr(Impulse.kr(pollRate), '/modValue', input, uniqueID);
			Out.kr(prOutBus, input);
		}).add;
	}

	cleanUp
	{
		this.releaseSynth;
		modControl.removeDependant(this);
		watcher.remove;
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