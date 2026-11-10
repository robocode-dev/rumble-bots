"""Validate statistical targeting against the original circular gun online."""
from ShotAim import ShotAim

class GatedAim(ShotAim):
    def __init__(self,mode=None):
        super().__init__('density')
        self.global_stats['density']=[0.,0];self.errors['density']=[0.,0]
        self.active='circular'

    def plan(self,e,origin,arena,power,turn,circular):
        point,diagnostic=super().plan(e,origin,arena,power,turn,circular)
        context=self.last_plan['context']
        local=self.local.setdefault(context,{n:[0.,0] for n in self.names})
        local.setdefault('density',[0.,0])
        available=diagnostic['model']=='density'
        if available:
            # Freeze both predictions BEFORE the eventual real-shot outcome.
            # Scoring is inherited and delayed until that real wave arrives.
            self.last_plan['points']['density']=point
        scores={}
        for name in ('circular','density'):
            value,count=local[name];mix=min(.65,count/24)
            scores[name]=(1-mix)*self.global_stats[name][0]+mix*value
        if available and self.global_stats['density'][1]>=3:
            best=max(scores,key=scores.get)
            if turn-self.last_switch>=8 and scores[best]>scores[self.active]+.02:
                self.active=best;self.last_switch=turn
        chosen=self.active if available else 'circular'
        diagnostic.update(model=chosen,validationScores=scores,densityScored=self.global_stats['density'][1])
        return (point if chosen=='density' else circular),diagnostic
