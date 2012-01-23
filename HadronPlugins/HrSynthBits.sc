HrPan : HadronPlugin {
	var monoButton, panSlider;

	*initClass {
		this.addHadronPlugin;
		ServerBoot.add {
			SynthDef('HrPan', { |inBus0, outBus0, monoIn = 0, pan = 0|
				var sig = InFeedback.ar(inBus0, 2);
				sig = XFade2.ar(
					Balance2.ar(sig[0], sig[1], pan),
					Pan2.ar(sig.sum, pan),
					Lag.kr(monoIn, 0.05) * 2 - 1
				);
				Out.ar(outBus0, sig);
			}).add;
		}
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		var bounds = Rect((Window.screenBounds.width - 350).rand, (Window.screenBounds.height - 70).rand, 350, 70);

		^super.new(argParentApp, this.name.asString, argIdent, argUniqueID, argExtraArgs, bounds, argNumIns: 2, argNumOuts: 2, argCanvasXY: argCanvasXY).init
	}

	init {
		monoButton = Button(window, Rect(10, 10, 150, 20))
		.states_([["Stereo-to-stereo"], ["Mono-to-stereo"]])
		.action_({ |view|
			synthInstance.set(\monoIn, view.value);
		});

		panSlider = HrEZSlider(window, Rect(10, 40, 330, 20), "pan", \bipolar, { |view|
			synthInstance.set(\pan, view.value);
		}, 0, labelWidth: 40);

		saveGets = [
			{ monoButton.value },
			{ panSlider.value }
		];

		saveSets = [
			{ |argg| monoButton.valueAction_(argg) },
			{ |argg| panSlider.valueAction_(argg) }
		];

		modGets.put(\pan, { panSlider.value });
		modSets.put(\pan, { |argg| defer { panSlider.valueAction_(argg) } });
		modMapSets.put(\pan, { |argg| defer { panSlider.value_(argg) } });

		synthInstance = Synth('HrPan', [
			inBus0: inBusses[0], outBus0: outBusses[0]
		] ++ this.getMapModArgs, group, \addToHead);
	}

	updateBusConnections {
		synthInstance.set(\inBus0, inBusses[0], \outBus0, outBusses[0])
	}

	cleanUp {}
}


HrFilter : HadronPlugin {
	var filtType, clipType, filtTypes, clipTypes, filtMenu, clipMenu;
	var preAmpSlider, postAmpSlider, params;

	*initClass {
		this.addHadronPlugin;
		StartUp.add {
			Spec.specs[\hadronrq] = ControlSpec(1.0, 0.01, \exp, 0, 1);
			Spec.specs[\hadrondecaytime] = ControlSpec(0.01, 10, \exp, 0, 0.1);
			Spec.specs[\hadrondb] = ControlSpec(-20, 20, \lin, 0, 0);
			Spec.specs[\hadronmooggain] = ControlSpec(0, 4);
		};
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		var bounds = Rect((Window.screenBounds.width - 350).rand, (Window.screenBounds.height - 190).rand, 350, 190);

		^super.new(argParentApp, this.name.asString, argIdent, argUniqueID, argExtraArgs, bounds, argNumIns: 2, argNumOuts: 2, argCanvasXY: argCanvasXY).init
	}

	init {
		var paramComp;
		var fixParamViews = {
			params.do { |sl, i|
				var argname = filtTypes[filtType][1][i * 2];
				if(argname.notNil) {
					sl.visible_(true).label_(argname).spec_(filtTypes[filtType][1][i * 2 + 1]);
					if(paramNotInited[i]) {
						sl.value = sl.spec.default;
						paramNotInited[i] = false;
					} {
						sl.value = sl.spec.constrain(sl.value);
					};
				} {
					sl.visible = false;
				}
			};
		};
		var paramNotInited = true ! 3;

		filtTypes = [
			// arrays are pairs: param name, spec name
			['LPF': #[freq, freq]],
			['HPF': #[freq, freq]],
			['RLPF': #[freq, freq, rq, hadronrq]],
			['RHPF': #[freq, freq, rq, hadronrq]],
			['BPF': #[freq, freq, rq, hadronrq]],
			['MoogFF': #[freq, freq, gain, hadronmooggain]],
			['Ringz': #[freq, freq, decaytime, hadrondecaytime]],
			['MidEQ': #[freq, freq, rq, hadronrq, db, hadrondb]]
		];
		clipTypes = #[none, distort, softclip, tanh];
		
		filtType = 0;
		StaticText(window, Rect(10, 10, 60, 20)).string_("filter");
		filtMenu = PopUpMenu(window, Rect(80, 10, 90, 20))
		.items_(filtTypes.collect(_[0]))
		.action_({ |view|
			filtType = view.value;
			fixParamViews.defer;
			this.makeSynth;
		});

		clipType = 0;
		StaticText(window, Rect(180, 10, 60, 20)).string_("clip");
		clipMenu = PopUpMenu(window, Rect(250, 10, 90, 20))
		.items_(clipTypes)
		.action_({ |view|
			clipType = view.value;
			this.makeSynth;
		});

		preAmpSlider = HrEZSlider(window, Rect(10, 40, 330, 20), "preamp", \hadrondb, { |view|
			synthInstance.set(\preamp, view.value.dbamp)
		});

		paramComp = CompositeView(window, Rect(5, 65, 340, 90))
		.background_(Color(0.8, 1, 0.8));
		params = Array.fill(3, { |i|
			var sl = HrEZSlider(paramComp, Rect(5, 5 + (30 * i), 330, 20), "", nil, { |view|
				var argname = filtTypes[filtType][1][i * 2];
				if(argname.notNil) { synthInstance.set(argname, view.value) };
			});
			if(i == 2) { sl.visible = false };
			sl
		});

		postAmpSlider = HrEZSlider(window, Rect(10, 160, 330, 20), "postamp", \hadrondb, { |view|
			synthInstance.set(\postamp, view.value.dbamp);
		});
		
		saveGets = [
			{ filtType },
			{ clipType },
			{ preAmpSlider.value },
			{ postAmpSlider.value },
			{ params.collect(_.value) }
		];

		saveSets = [
			{ |argg|
				filtType = argg;
				defer { filtMenu.value = argg };
				this.makeSynth;
			},
			{ |argg|
				clipType = argg;
				defer { clipMenu.value = argg };
				this.makeSynth;
			},
			{ |argg| preAmpSlider.valueAction = argg },
			{ |argg| postAmpSlider.valueAction = argg },
			{ |argg| params.do({ |sl, i| sl.valueAction = argg[i] }) }
		];

		modGets = (
			preamp: { preAmpSlider.value },
			postamp: { postAmpSlider.value }
		);
		params.do { |sl, i| modGets.put(("param" ++ i).asSymbol, { sl.value }) };

		modSets = (
			preamp: { |argg| defer { preAmpSlider.valueAction = argg } },
			postamp: { |argg| defer { postAmpSlider.valueAction = argg } }
		);
		params.do { |sl, i| modSets.put(("param" ++ i).asSymbol, { |argg| sl.valueAction = argg }) };

		modMapSets = (
			preamp: { |argg| defer { preAmpSlider.value = argg } },
			postamp: { |argg| defer { postAmpSlider.value = argg } }
		);
		params.do { |sl, i| modMapSets.put(("param" ++ i).asSymbol, { |argg| defer { sl.value = argg } }) };

		fixParamViews.value;
		this.makeSynth;
	}

	releaseSynth { synthInstance.free; synthInstance = nil; }

	synthArgs {
		var parmValues = Array(6);
		filtTypes[filtType][1].pairsDo { |name, spec, i|
			parmValues.add(name).add(params[i div: 2].value);
		};
		^[inBus0: inBusses[0], outBus0: outBusses[0],
			preamp: preAmpSlider.value.dbamp, postamp: postAmpSlider.value.dbamp,
		] ++ parmValues ++ this.getMapModArgs
	}

	makeSynthDef {
		SynthDef("HrFilter" ++ uniqueID, { |inBus0, outBus0, preamp = 1, postamp = 1|
			var parmValues = Array(3),
			sig = InFeedback.ar(inBus0, 2);
			filtTypes[filtType][1].pairsDo { |name, spec|
				parmValues.add(NamedControl(name, spec.asSpec.default, lags: 0.05, fixedLag: true))
			};
			sig = filtTypes[filtType][0].asClass.ar(sig * preamp, *parmValues);
			if(clipType > 0) {
				sig = sig.perform(clipTypes[clipType]);
			};
			Out.ar(outBus0, sig * postamp);
		}).add;
	}

	mapModCtl { |paramName, ctlBus|
		var node;
		paramName = paramName.asString;
		if(paramName.beginsWith("param")) {
			paramName = filtTypes[filtType][1][paramName[5..].asInteger];
		};
		if(ctlBus == -1 or: { ctlBus.tryPerform(\index) == -1 }) {
			mappedMods.removeAt(paramName.asSymbol);
		} {
			mappedMods.put(paramName.asSymbol, ctlBus);
		};
		if((node = this.tryPerform(\synthInstance)).notNil) {
			node.map(paramName, ctlBus);
		};
	}
	getMapModArgs {
		var result = Array(mappedMods.size * 2);
		mappedMods.keysValuesDo { |param, bus|
			param = param.asString;
			if(param.beginsWith("param")) {
				param = filtTypes[filtType][1][param[5..].asInteger];
			};
			case { bus.isNumber } {
				result.add(param).add(("c" ++ bus).asSymbol)
			}
			{ bus.isKindOf(Bus) } {
				result.add(param).add(bus.asMap)
			}
			// else, invalid map - ignore
		};
		^result
	}

	updateBusConnections {
		synthInstance.set(\inBus0, inBusses[0], \outBus0, outBusses[0])
	}

	cleanUp {}
}