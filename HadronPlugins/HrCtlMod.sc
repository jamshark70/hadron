HrCtlMod : HrSimpleModulator {
	*initClass
	{
		this.addHadronPlugin;
	}
	*height { ^215 }
	// copy and paste programming...
	init
	{
		window.background_(Color.gray(0.7));
		prOutBus = Bus.control(Server.default, 1);
		helpString = "Write a modulation function, maybe using an audio input.";
		StaticText(window, Rect(10, 20, 150, 20)).string_("Modulation function");

		postOpFunc = {|sig| (sig * 0.5) + 0.5; };

		// fix this
		postOpText = TextField(window, Rect(10, 20, 430, 20))
		.string_("{ |sig| SinOsc.kr(1, 0, 0.5, 0.5) }")
		.action_({|txt|
			postOpFunc = txt.value.interpret;
			this.refreshSynth;
		});

		modControl = HadronModTargetControl(window, Rect(10, 50, 430, 20), parentApp);
		modControl.addDependant(this);

		startButton = Button(window, Rect(10, 80, 80, 20)).states_([["Start"],["Stop"]])
		// not sure about this -- better to sync with actual mapped state
		// that's harder than I can do right this minute
		.value_(1)
		.action_
		({|btn|
			// synthInstance.run(btn.value == 1)
			if(btn.value == 1) { modControl.map(prOutBus) }
			{ modControl.unmap };
		});

		this.refreshSynth;

		saveGets =
			[
				{ postOpText.string; },
				{ modControl.getSaveValues; },
				{ startButton.value; }
			];

		saveSets =
			[
				{|argg| postOpText.valueAction_(argg); },
				{|argg| modControl.putSaveValues(argg); },
				{|argg| startButton.valueAction_(argg); }
			];
	}

	refreshSynth {
		// it's a little bit dumb that I have to do this, but
		// it's the only way to conditionally not execute something after try
		var shouldPlay = true;
		fork
		{
			try {
				SynthDef("hrSimpleMod"++uniqueID, { |prOutBus, inBus0|
					var input = InFeedback.ar(inBus0);
					input = postOpFunc.value(input);
					if(input.size > 1 or: { input.rate != \control }) {
						// throw prevents the synthdef from being replaced
						Exception("HrCtlMod result must be one channel, control rate").throw;
					};
					Out.kr(prOutBus, A2K.kr(input));
				}).add;
			} { |err|
				if(err.isKindOf(Exception)) {
					shouldPlay = false;
					err.reportError;
					defer { parentApp.displayStatus(err.errorString, -1) };
				};
			};
			if(shouldPlay) {
				Server.default.sync;
				if(synthInstance.notNil) {
					synthInstance = Synth("hrSimpleMod"++uniqueID,
						[\inBus0, inBusses[0], \prOutBus, prOutBus],
						target: synthInstance, addAction: \addReplace
					).debug("HrCtlMod: playing");
				} {
					synthInstance = Synth("hrSimpleMod"++uniqueID,
						[\inBus0, inBusses[0], \prOutBus, prOutBus],
						target: group
					).debug("HrCtlMod: playing");
				};
			};
		};
	}
	cleanUp
	{
		synthInstance.free;
		modControl.removeDependant(this);
	}

	update { |obj, what, argument, oldplug, oldparam|
		if(#[currentSelPlugin, currentSelParam].includes(what)) {
			if(argument.notNil) {
				modControl.unmap(oldplug, oldparam);
				modControl.map(prOutBus)
			} {
				modControl.unmap(oldplug, oldparam);
			};
		}
	}
}