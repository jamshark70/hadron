HrCtlMod : HrSimpleModulator {
	*initClass
	{
		this.addHadronPlugin;
	}
	// copy and paste programming...
	init
	{
		window.background_(Color.gray(0.7));
		prOutBus = Bus.control(Server.default, 1);
		helpString = "Input is modulation source (audio). Applies the operation and modulates the target parameter.";
		StaticText(window, Rect(10, 20, 150, 20)).string_("Operation on signal \"sig\":");

		postOpFunc = {|sig| (sig * 0.5) + 0.5; };

		postOpText = TextField(window, Rect(160, 20, 280, 20)).string_("(sig * 0.5) + 0.5;")
		.action_({|txt|
			postOpFunc = ("{|sig|"+ txt.value + "}").interpret;
			this.refreshSynth;
		});

		modControl = HadronModTargetControl(window, Rect(10, 50, 430, 20), parentApp);
		modControl.addDependant(this);

		startButton = Button(window, Rect(10, 80, 80, 20)).states_([["Start"],["Stop"]])
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
		fork
		{
			SynthDef("hrSimpleMod"++uniqueID, { |prOutBus, inBus0|
				var input = InFeedback.ar(inBus0);
				input = postOpFunc.value(input);
				Out.kr(prOutBus, A2K.kr(input));
			}).add;
			Server.default.sync;
			if(synthInstance.notNil) {
				synthInstance = Synth("hrSimpleMod"++uniqueID,
					[\inBus0, inBusses[0], \prOutBus, prOutBus],
					target: synthInstance, addAction: \addReplace
				);
			} {
				synthInstance = Synth("hrSimpleMod"++uniqueID,
					[\inBus0, inBusses[0], \prOutBus, prOutBus],
					target: group
				);
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