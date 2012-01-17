HrMultiCtlMod : HrCtlMod {
	var modCtlsScroll;
	var loadSemaphore;

	*initClass
	{
		this.addHadronPlugin;
	}
	*height { ^335 }

	init
	{
		loadSemaphore = Semaphore(1);
		window.background_(Color.gray(0.7));
		if(extraArgs.size >= 1) {
			numChannels = max(1, extraArgs[0].asInteger);
			if(extraArgs[2].size > 0 and: { extraArgs[2].asFloat > 0 }) {
				pollRate = extraArgs[2].asFloat;
			} {
				pollRate = defaultPollRate;
			};
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
			postOpFunc = this.fixFuncString(postOpText.string).interpret;
			this.makeSynth;
		});

		startButton = Button(window, Rect(100, 120, 80, 20)).states_([["Start"],["Stop"]])
		.value_(0)
		.action_
		({|btn|
			if(btn.value == 1) {
				isMapped = modControl.collect { |ctl, i| ctl.map(prOutBus.index + i) };
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
					fork {
						loadSemaphore.wait; // block others
						this.makeSynth;
						loadSemaphore.signal; // unblock others
					};
				},
				{ |argg|
					{
						loadSemaphore.wait;
						modControl.do { |ctl, i|
							ctl.putSaveValues(argg[i]).doWakeFromLoad
						};
						loadSemaphore.signal;
					}.fork(AppClock);
				},
				{ |argg|
					{
						loadSemaphore.wait;
						startButton.valueAction_(argg);
						loadSemaphore.signal;
					}.fork(AppClock);
				},
				{ |argg|
					{
						loadSemaphore.wait;
						if(argg.notNil) {
							pollRate = argg;
							pollRateView.tryPerform(\valueAction_, argg);
						};
						loadSemaphore.signal;
					}.fork(AppClock);
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

	synthArgs { ^[
		inBus0: inBusses[0], prOutBus: prOutBus, outBus0: outBusses[0],
		pollRate: pollRate * isMapped.any({ |bool| bool }).binaryValue
			* (watcher.notNil.binaryValue)
	] }

	// change here: we're not checking numchannels
	// now, numchannels of the func determines number of modtargets
	makeSynthDef {
		var numch;
		replyID = UniqueID.next;
		SynthDef("HrMultiCtlMod"++uniqueID, { |prOutBus, inBus0, outBus0, pollRate = 0|
			var input = A2K.kr(InFeedback.ar(inBus0));
			input = postOpFunc.value(input).asArray;
			if(input.any { |unit| unit.rate != \control }) {
				// throw prevents the synthdef from being replaced
				Exception("HrMultiCtlMod: all channels must be control rate").throw;
			};
			numch = input.size;
			SendReply.kr(Impulse.kr(pollRate), '/modValue', input, replyID);
			Out.kr(prOutBus, input);
			Out.ar(outBus0, K2A.ar(input));
		}).add;
		this.rebuildBus(numch).rebuildTargets(numch);
		numChannels = numch;
		isMapped = isMapped.extend(numChannels, false);
	}

	rebuildBus { |newChannels|
		var oldKrBus, oldArBus, endWatcher, tempBusIndex;
		if(newChannels != numChannels) {
			oldKrBus = prOutBus;
			oldArBus = outBusses[0];
			prOutBus = Bus.control(Server.default, newChannels);
			tempBusIndex = Server.default.audioBusAllocator.alloc(newChannels);
			// nb: if newChannels < numChannels, some of these will be invalid
			// but we're keeping them only for setOutBus calls
			// the extras will be discarded later: outBusses.keep(newChannels)
			outBusses = Array.fill(max(numChannels, newChannels), { |i|
				Bus(\audio, tempBusIndex + i, 1, Server.default);
			});
			if(newChannels < numChannels) {
				modControl[newChannels..].do { |ctl, i|
					ctl.unmap;
					this.setOutBus(nil, nil, newChannels + i);
				};
				// outConnections = outConnections.keep(newChannels);
			} {
				// expand outConnections array, if needed
				outConnections = outConnections.extend(newChannels);
				(numChannels .. newChannels-1).do { |i|
					outConnections[i] = nil ! 2;
				};
				mainOutBusses = mainOutBusses.extend(newChannels, parentApp.blackholeBus);
			};
			badValueSynth = this.makeBadValueSynth;  // replace for new # of channels
			boundCanvasItem.setBlobs(numOutBlobs: newChannels);
			min(newChannels, modControl.size).do { |i|
				modControl[i].map(prOutBus.index + i);
				this.setOutBus(outConnections[i][0], outConnections[i][1], i);
			};
			outBusses = outBusses.keep(newChannels);
			if(synthInstance.notNil) {
				synthInstance.register;
				endWatcher = SimpleController(synthInstance).put(\n_end, {
					endWatcher.remove;
					oldKrBus.free;
					oldArBus.free;
				});
			} {
				oldKrBus.free;
				oldArBus.free;
			}
		};
	}

	rebuildTargets { |newChannels|
		var offset, temp, guifunc;
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
				guifunc = {
					// wait for synth to be ready
					// AND, more important, make other threads wait for me
					if(loadSemaphore.notNil) {
						loadSemaphore.wait;
					};
					(newChannels - offset).do { |i|
						temp = HadronModTargetControl(
							modCtlsScroll,
							Rect(5, 5 + (25 * (i+offset)), 400, 20),
							parentApp, this
						);
						modControl = modControl.add(temp);
						temp.addDependant(this);
					};
					if(loadSemaphore.notNil) {
						loadSemaphore.signal
					};
				};
				if(thisThread.isKindOf(Routine) and: { thisThread.clock === AppClock }) {
					guifunc.value
				} {
					guifunc.fork(AppClock);
				};
			};
		};
	}

	update { |obj, what, argument, oldplug, oldparam|
		var i, anymapped, err;
		if(#[currentSelPlugin, currentSelParam].includes(what)) {
			i = modControl.indexOf(obj);
			if(i.isNil) {
				err = Error("STRANGE ERROR: HrMultiCtlMod got update from a target control that it doesn't own");
				parentApp.displayStatus(err.errorString, -1);
				err.throw;
			};
			if(argument.notNil) {
				modControl[i].unmap(oldplug, oldparam);
				isMapped[i] = modControl[i].map(prOutBus.index + i);
				anymapped = isMapped.any({ |bool| bool }).binaryValue;
				synthInstance.set(\pollRate,
					pollRate * anymapped * (watcher.notNil.binaryValue)
				);
				defer { startButton.value = anymapped };
			} {
				modControl[i].unmap(oldplug, oldparam);
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

	// "not to be overwritten" says HadronPlugin
	// but I need to delay this until we know how many channels
	// are needed
	wakeConnections {
		var saveOutConnections = outConnections.collect(_.copy);
		// this should wait until after HadronStateLoad
		// finishes its synchronous stuff, including injectSaveValues
		// by then, all the other threads should be queued up in the semaphore
		// so this should be the last thing to go
		// this is really just too clever for words...
		// which means it'll probably break sometime. oh well.
		// like, if HadronStateLoad ever becomes threaded
		fork {
			fork {
				loadSemaphore.wait;
				outConnections = saveOutConnections;
				super.wakeConnections;
				this.prUpdateBusConnections;
				defer { parentApp.canvasObj.drawCables };
			}
		}
	}
}