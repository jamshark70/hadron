HrMultiCtlMod : HrCtlMod {
	var modCtlsScroll;

	*initClass
	{
		this.addHadronPlugin;
	}
	*height { ^335 }

	*shouldWatch { |argExtraArgs|
		^argExtraArgs.size < 1 or: {
			argExtraArgs[0] != "0" and: { argExtraArgs[0] != "false" }
		}
	}

	init
	{
		window.background_(Color.gray(0.7));
		if(extraArgs.size >= 1 and: {
			extraArgs[1].size > 0 and: { extraArgs[1].asFloat > 0 }
		}) {
			pollRate = extraArgs[1].asFloat;
		} {
			pollRate = defaultPollRate;
		};
		helpString = "Write a modulation function, maybe using an audio input.";
		StaticText(window, Rect(10, 20, 150, 20)).string_("Modulation function");

		numChannels = 0; // will change when synth is made
		isMapped = [];
		modControl = [];

		postOpFunc = {|sig| (sig * 0.5) + 0.5; };

		postOpText = TextView(window, Rect(10, 20, 430, 95))
		.string_("{ |sig| SinOsc.kr(1, 0, 0.5, 0.5) }");

		evalButton = Button(window, Rect(10, 120, 80, 20))
		.states_([["Evaluate"]])
		.action_({
			postOpFunc = postOpText.string.interpret;
			this.makeSynth;
		});

		startButton = Button(window, Rect(100, 120, 80, 20)).states_([["Start"],["Stop"]])
		.value_(0)
		.action_
		({|btn|
			if(btn.value == 1) {
				isMapped = modControl.collect { |ctl| ctl.map(prOutBus) };
				synthInstance.set(\pollRate, pollRate * (watcher.notNil.binaryValue));
			} {
				modControl.do(_.unmap);
				isMapped = false ! numChannels;
				synthInstance.set(\pollRate, 0);
			};
		});

		if(this.shouldWatch) {
			pollRateView = HrEZSlider(window, Rect(10, 150, 430, 20),
				"update rate", [1, 25], { |view|
					pollRate = view.value;
					if(synthInstance.notNil) {
						synthInstance.set(\pollRate, pollRate * (watcher.notNil.binaryValue));
					};
				}, pollRate, labelWidth: 100, numberWidth: 45
			);
		};

		modCtlsScroll = ScrollView(window, Rect(5, 175, 440, 150))
		.background_(Color.gray(0.92))
		.hasHorizontalScroller_(false);

		this.makeSynth;

		saveGets =
			[
				{ postOpText.string; },
				{ modControl.collect(_.getSaveValues); },
				{ startButton.value; },
				{ pollRate }
			];

		saveSets =
			[
				{ |argg|
					postOpText.string_(argg);
					// previously called "evalButton.doAction"
					// but in swing, can't rely on gui synchronous-icity
					postOpFunc = argg.interpret;
					this.makeSynth;
				},

				// hm, here's a big problem

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
			watcher = OSCresponderNode(Server.default.addr, '/modValue', { |time, resp, msg|
				if(msg[2] == replyID) {
					modControl.do { |ctl, i| ctl.updateMappedGui(msg[3 + i]) };
				};
			}).add;
		};
	}

	cleanUp
	{
		this.releaseSynth;
		modControl.do(_.removeDependant(this));
		watcher.remove;
		prOutBus.free;
	}

	synthArgs { ^[\inBus0, inBusses[0], \prOutBus, prOutBus,
		pollRate: pollRate * isMapped.any({ |bool| bool }).binaryValue * (watcher.notNil.binaryValue)
	] }

	// change here: we're not checking numchannels
	// now, numchannels of the func determines number of modtargets
	makeSynthDef {
		var numch;
		replyID = UniqueID.next;
		SynthDef("HrMultiCtlMod"++uniqueID, { |prOutBus, inBus0, pollRate = 0|
			var input = A2K.kr(InFeedback.ar(inBus0));
			input = postOpFunc.value(input).asArray.debug("sig");
			if(input.any { |unit| unit.rate != \control }) {
				// throw prevents the synthdef from being replaced
				Exception("HrMultiCtlMod: all channels must be control rate").throw;
			};
			numch = input.size.debug("numch");
			SendReply.kr(Impulse.kr(pollRate), '/modValue', input, replyID);
			Out.kr(prOutBus, input);
		}).add;
		this.rebuildBus(numch).rebuildTargets(numch);
		numChannels = numch;
		isMapped = isMapped.extend(numChannels, false);
	}

	rebuildBus { |newChannels|
		var oldBus, endWatcher;
		if(newChannels != numChannels) {
			oldBus = prOutBus;
			prOutBus = Bus.control(Server.default, numChannels);
			modControl.do { |ctl, i| ctl.map(prOutBus.index + i) };
			if(newChannels < numChannels) {
				modControl[newChannels..].do(_.unmap);
			};
			if(synthInstance.notNil) {
				synthInstance.register;
				endWatcher = SimpleController(synthInstance).put(\n_end, {
					endWatcher.remove;
					oldBus.free;
				});
			} {
				oldBus.free
			}
		};
	}

	rebuildTargets { |newChannels|
		var offset, temp;
		if(newChannels < numChannels) {
			temp = modControl.copy;
			modControl = modControl.keep(newChannels);
			defer {
				temp[newChannels..].do { |ctl|
					ctl.remove;
					ctl.removeDependant(this);
				};
			};
		} {
			if(newChannels > numChannels) {
				offset = numChannels;
				defer {
					(newChannels - offset).do { |i|
						temp = HadronModTargetControl(
							modCtlsScroll,
							Rect(5, 5 + (25 * (i+offset)), 400, 20),
							parentApp, this
						);
						modControl = modControl.add(temp);
						temp.addDependant(this);
					};
				};
			};
		};
	}

	update { |obj, what, argument, oldplug, oldparam|
		var i, anymapped;
		if(#[currentSelPlugin, currentSelParam].includes(what)) {
			i = modControl.indexOf(obj);
			if(argument.notNil) {
				modControl.unmap(oldplug, oldparam);
				isMapped[i] = modControl.map(prOutBus.index + i);
				anymapped = isMapped.any({ |bool| bool }).binaryValue;
				synthInstance.set(\pollRate,
					pollRate * anymapped * (watcher.notNil.binaryValue)
				);
				defer { startButton.value = anymapped };
			} {
				modControl.unmap(oldplug, oldparam);
				synthInstance.set(\pollRate, 0);
				isMapped[i] = false;
				defer { startButton.value = 0 };
			};
		};
	}

	targetControlSize { ^1 }

	notifyPlugKill { |argPlug|
		modControl.do(_.plugRemoved(argPlug));
	}
	
	notifyPlugAdd { |argPlug|
		modControl.do(_.plugAdded);
	}
	
	wakeFromLoad {
		modControl.do(_.doWakeFromLoad);
	}
}