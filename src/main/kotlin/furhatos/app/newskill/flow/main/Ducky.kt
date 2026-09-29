package furhatos.app.newskill.flow.main

import furhatos.app.newskill.flow.Parent
import furhatos.app.newskill.nlu.CalmDown
import furhatos.flow.kotlin.State
import furhatos.flow.kotlin.furhat
import furhatos.flow.kotlin.onResponse
import furhatos.flow.kotlin.state
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes

val Ducky: State = state(Parent) {
    onEntry {
        // Listen to what the user has to say, or maybe even prompt the user?
    }
    
    onResponse<CalmDown> {
        // Calm the robot down
    }
    
    onResponse {
        // Do Gemini calls
        // Return text and gestures
        // Speak text and perform gestures
        // Increase robot volatility
    }
}