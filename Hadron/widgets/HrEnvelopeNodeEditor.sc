HrEnvelopeNodeEditor {
	var envView, loopNode, releaseNode, loopText, releaseText;
	var <>action, <>insertAction, <>deleteAction, <>curveAction, <>nodeAction;

	*new { |parent, envBounds, loopBounds, releaseBounds|
		^super.new.init(parent, envBounds, loopBounds, releaseBounds)
	}

	init { |parent, envBounds, loopBounds, releaseBounds|
		envView = HrEnvelopeView(parent, envBounds)
		.env_(Env.adsr)   // must have a default
		.action_({ action.(this) })
		.insertAction_({
			this.updateMenu;
			insertAction.(this);
		})
		.deleteAction_({
			this.updateMenu;
			deleteAction.(this);
		})
		.curveAction_({ curveAction.(this) });

		if(loopBounds.isNil) {
			loopBounds = envBounds.resizeTo(envBounds.width - 10 / 2, 20)
			.top_(envBounds.bottom + 5);
		};
		loopText = StaticText(parent, loopBounds.copy.width_(35)).string_("loop");
		loopNode = PopUpMenu(parent,
			loopBounds.copy.width_(loopBounds.width - 45)
			.left_(loopBounds.left + 45)
		)
		.action_({ |view|
			if(view.value == 0) {
				envView.loopNode = nil
			} {
				envView.loopNode = view.value - 1;
			};
			envView.updateEnvView;
			nodeAction.(this);
		});

		if(releaseBounds.isNil) {
			releaseBounds = loopBounds.copy.left_(loopBounds.right + 10);
		};
		releaseText = StaticText(parent, releaseBounds.copy.width_(35)).string_("rel");
		releaseNode = PopUpMenu(parent,
			releaseBounds.copy.width_(releaseBounds.width - 45)
			.left_(releaseBounds.left + 45)
		)
		.action_({ |view|
			if(view.value == 0) {
				envView.releaseNode = nil
			} {
				envView.releaseNode = view.value - 1;
			};
			envView.updateEnvView;
			nodeAction.(this);
		});

		this.updateMenu;
	}

	env { ^envView.value }
	value { ^envView.value }
	env_ { |envel|
		envView.env = envel;
		this.updateMenu;
	}

	updateMenu {
		loopNode.items = ["None"] ++ Array.fill(envView.curves.size - 1, _.asString);
		releaseNode.items = loopNode.items;
		loopNode.value = (envView.loopNode ? -1) + 1;
		releaseNode.value = (envView.releaseNode ? -1) + 1;
	}

	remove {
		[envView, loopNode, releaseNode, loopText, releaseText].do(_.remove);
	}
}