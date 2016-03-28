package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Peasant;
import edu.cwru.sepia.agent.planner.Position;

public class MoveAction implements StripsAction {
	Peasant peasant;
	Position destination;

	public MoveAction(Peasant peasant, Position destination){
		this.peasant = peasant;
		this.destination = destination;
	}

	@Override
	public boolean preconditionsMet(GameState state) {
		return !peasant.getPosition().equals(destination) && !state.isOccupied(destination);
	}

	@Override
	public GameState apply(GameState state) {
		state.applyMoveAction(this, peasant.getId(), destination);
		return state;
	}

	@Override
	public Action createSepiaAction() {
		return Action.createCompoundMove(peasant.getId(), destination.x, destination.y);
	}

	@Override
	public int getPeasantId() {
		return peasant.getId();	
	}

}
