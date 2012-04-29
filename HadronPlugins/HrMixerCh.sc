HrMixerCh : HadronPlugin {
	// mcgui is index into ddwChucklib MCG collection
	var <mixerName, <mixer, <mcgui, levelModSl;
	var iMadeMixer = false, initLevelIfIMakeMixer = 0.75;

	var mixerNameMenu, mixerNameField, mcgMenu;

	*initClass {
		if('MixerChannel'.asClass.notNil) {
			this.addHadronPlugin;

			StartUp.add {
				ServerBoot.add {
					[{ |in| in.sum }, { |in| in }].do { |func, i|
						SynthDef("HrMixerCh-in" ++ (i+1), { |inBus0, outBus0, level, gate = 1|
							var sig = func.value(InFeedback.ar(inBus0, 2)),
							eg = EnvGen.kr(Env.asr(0.1, 1, 0.1), gate, doneAction: 2);
							level = level.lag(0.08);
							Out.ar(outBus0, sig * level * eg);
						}).add;
					};
				};
			};
		};
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		^super.new(argParentApp, this.name, argIdent, argUniqueID, argExtraArgs, Rect((Window.screenBounds.width - 366).rand, (Window.screenBounds.height - 100).rand, 366, 100), 2, 0, argCanvasXY).init
	}

	init {
		var flow = FlowView(window, window.bounds);

		levelModSl = HrEZSlider(flow, 352@20, "Level", \amp, { |view|
			synthInstance.set(\level, view.value)
		}, 1);

		StaticText(flow, 150@20).string_("Mixer name").align_(\right);
		mixerNameMenu = PopUpMenu(flow, 120@20)
		// .items_(this.mixerNames)
		.action_({ |view|
			this.mixerName = view.items[view.value];
		});
		Button(flow, 80@20)
		.states_([["refresh"]])
		.action_({ |view|
			this.refreshMenu;
		});			

		StaticText(flow, 150@20).string_("Type a name").align_(\right);
		mixerNameField = TextField(flow, 120@20)
		.action_({ |view|
			this.mixerName = view.value;
		});

		if('MCG'.asClass.notNil) {
			StaticText(flow, 150@20).string_("MixingBoard index").align_(\right);
			mcgMenu = PopUpMenu(flow, 120@20)
			// .items_(["None"] ++ MCG.keys.collect(_.asString))
			.action_({ |view| this.mcgui = view.value - 1 });
		};

		this.refreshMenu;

		if(extraArgs.size > 0) {
			if(extraArgs[2].notNil) {
				initLevelIfIMakeMixer = extraArgs[2].asFloat;
			};
			this.mixerName = extraArgs[0];
			if(extraArgs[1].notNil) {
				this.mcgui = extraArgs[1].asInteger;
			};
		};

		saveGets = [
			{ [mixerName.asSymbol, mcgui] },
			{ levelModSl.value }
		];

		saveSets = [
			{ |argg| defer { this.mixerName_(argg[0]).mcgui_(argg[1]) } },
			{ |argg| levelModSl.valueAction_(argg) }
		];

		modGets.put(\level, { levelModSl.value });
		modMapSets.put(\level, { |argg| defer { levelModSl.value = argg } });
		modSets.put(\level, { |argg| defer { levelModSl.valueAction = argg } });
	}

	mixerName_ { |name|
		var existing;
		name = name.asSymbol;
		if(name != mixerName) {
			existing = MixerChannel.servers[Server.default].tryPerform(\detect, { |mixer|
				mixer.name.asSymbol === name
			});
			if(iMadeMixer) { mixer.free };
			if(existing.notNil) {
				mixer = existing;
				iMadeMixer = false;
			} {
				mixer = MixerChannel(name, Server.default, 2, 2, level: initLevelIfIMakeMixer);
				iMadeMixer = true;
			};
			mixerName = name;
			if(MCG.tryPerform(\exists, mcgui ? -1)) { mixer => MCG(mcgui) };
			this.makeSynth.refreshMenu;
		};
	}

	mcgui_ { |index|
		index = index.asInteger;
		if(mcgui != index and: { MCG.tryPerform(\exists, index ? -1) }) {
			if(MCG.tryPerform(\exists, mcgui ? -1)) { MCG(mcgui).v.mixer = nil };
			mixer => MCG(index);
			mcgui = index;
			defer { this.refreshMenu };
		};
	}

	refreshMenu {
		var names = ['None'];

		if(MixerChannel.servers[Server.default].notNil) {
			names = names ++ MixerChannel.servers[Server.default].values
			.collect({ |mc| mc.name.asSymbol })
			.sort;
		};

		mixerNameMenu.items_(names)
		.value_(names.indexOfEqual(mixerName) ? 0);
		mixerNameField.string = mixerName;

		mcgMenu.items_(names = ["None"] ++ MCG.keys.collect(_.asString))
		.value_(names.indexOfEqual(mcgui.asString) ? 0);
	}

	makeSynth {
		if(synthInstance.notNil) {
			Server.default.makeBundle(0.2, { this.releaseSynth });
		};
		if(mixer.notNil) {
			mixer.doWhenReady {
				synthInstance = Synth("HrMixerCh-in" ++ mixer.inChannels,
					[inBus0: inBusses[0], outBus0: mixer.inbus, level: levelModSl.value],
					mixer.synthgroup, \addToTail
				);
			};
		};
	}

	hasGate { ^true }

	updateBusConnections {
		synthInstance.set(\inBus0, inBusses[0]);
	}

	cleanUp {
		this.releaseSynth;
		if(iMadeMixer) { mixer.free };
	}
}
