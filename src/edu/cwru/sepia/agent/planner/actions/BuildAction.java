package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;

public class BuildAction implements StripsAction {
	int townhallId;
	int peasantTemplateId;	
	
	public BuildAction(int townhallId, int peasantTemplateId){
		this.townhallId = townhallId;
		this.peasantTemplateId = peasantTemplateId;
	}

	@Override
	public boolean preconditionsMet(GameState state) {
		return state.canBuild();
	}

	@Override
	public GameState apply(GameState state) {
		state.applyBuildAction(this);
		return state;
	}

	@Override
	public Action createSepiaAction() {
		return Action.createPrimitiveProduction(townhallId, peasantTemplateId);
	}
	
	@Override
	public int getPeasantId() {
		return townhallId;	
	}

}
