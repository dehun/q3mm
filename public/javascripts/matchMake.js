var baseUrl = "http://localhost:9000"

var LoginBox = React.createClass({
    render: function() {
            return (
            <div>
                    <p>Quake Live Duel Matchmaking and ad-hoc/on-demand duel server hosting.
                    Servers got killed on inactivity timeout(no enough players, match do not start for to long).
                    In order to use this service you have to first login via Steam Open Id.
                    </p>
                    <LoginButton/>
            </div>)
    }
})

var MatchMakeState = React.createClass({
    render: function() {
        return (<div id="mm_state">{this.props.state}</div>)
    }
})

var MatchMakeFail = React.createClass({
    render: function() {
        return (<div id="mm_fail">{this.props.reason}</div>)
    }
})

var ConnectToServer = React.createClass({
    render: function() {
        setTimeout(function() {
            window.location.replace(this.props.server)
        }, 3000)
        return (
                <div id="connect_to_server">
                  <div id="server_description">
                    Clink on the link to connect to server.
                  </div>
                  <div id="server_link_box">
                    <a href="{this.props.server}">{this.props.server}</a>
                  </div>
                </div>
        )
    }
})
 
var MatchMakeButton = React.createClass({
    render: function() {
        return (<div id="match_make_button" onClick={this.onClick}>Start match</div>)
    },
    onClick: function() {
        var socket = new WebSocket(wshost);
        this.props.onWsConnecting()
        socket.onopen = this.props.onWsOpen
        socket.onclose = this.props.onWsClosed
        socket.onmessage = this.props.onWsMessage
    }
})

var MatchMakeStartBox = React.createClass({
    getInitialState: function() {
        return {"phase": "idle"}
    },
    onWsConnecting: function() {
        this.setState({"phase": "connecting"})
    },
    onWsOpen: function() {
        this.setState({"phase": "connected"})
    },
    onWsClosed: function() {
        if (this.state.phase != "fail") {
            this.setState({"phase": "fail", "reason": "remote closed the connection"})
        }
    },
    onWsMessage: function(msgs) {
        if (msgs.type == 'open') {
            this.onWsOpen();
            return;
        }
        if (typeof msgs.data == 'undefined') return
        msg = JSON.parse(msgs.data)
        switch (msgs.cmd) {
        case "enqueued":
            this.setState({"phase": "enqueued"})
            break
        case "newChallenge":
            this.setState({"phase": "newChallenge", "server": msg.body.server})
            break
        case "noCompetition":
            this.setState({"phase": "fail", "reason": "no competition"})
            break
        }
    },
    render: function() {
        switch (this.state.phase) {
        case "idle":
            return (<MatchMakeButton onWsConnecting={this.onWsConnecting}
                    onWsOpen={this.onWsOpen} onWsClosed={this.onWsClosed} onWsOpen={this.onWsMessage}/>)
        case "connecting":
            return (<MatchMakeState state="connecting"/>)
        case "connected":
            return (<MatchMakeState state="awaiting server state"/>)
        case "enqueued":
            return (<MatchMakeState state="awaiting challenge"/>)
        case "newChallenge":
            return (<ConnectToServer server={this.state.server}/>)
        case "fail":
            return (<MatchMakeFail reason={this.state.reason}/>)
        } 
    }
})

var OnDemandStartButton = React.createClass({
    render: function() {
        return (<div id="on_demand_start_button" onClick={this.onClick}>on demand start</div>)
    },
    onClick: function() {
        this.props.onRequested()
        $.ajax({
            url: baseUrl + "/requestServer",
            dataType: 'json',
            cache: false,
            success: function(data) {
                this.props.onSuccess(data)
            }.bind(this),
            error: function(xhr, status, err) {
                console.error("", status, err.toString());
                this.props.onError(err.toString())
            }.bind(this)
        });
    }
})

var OnDemandStartBox = React.createClass({
    getInitialState: function() {
        return {"phase": "idle"}
    },
    render: function() {
        switch (this.state.phase) {
        case "idle":
            return (<OnDemandStartButton
                    onRequested={this.onServerRequested}
                    onSuccess={this.onServerRequestSuccess}
                    onError={this.onServerRequestFailure}/>)
        case "requested":
            return (<MatchMakeState state="awaiting server state"/>)
        case "created":
            return (<ConnectToServer server={this.state.server}/>)
        case "fail":
            return (<MatchMakeFail reason={this.state.reason}/>)
        }
    },
    onServerRequested: function() {
        this.setState({"phase": "requested"})
    },
    onServerRequestSuccess: function(msg) {
        this.setState({"phase": "created", "server": msg.server})
    },
    onServerRequestFailure: function(reason) {
        this.setState({"phase": "fail", "reason": reason})
    }
})

var MatchMakeBox = React.createClass({
    render: function() {
        if (mode == "matchmake") {
        return (<div>
                  <MatchMakeStartBox/>
                </div>)
        } else {
            return (<div>
                      <OnDemandStartBox/>
                    </div>)
        }
    }
})

var LoggedInBox = React.createClass({
    render: function() {
        return (
                <div id="logged_in_box">
                  <span>Welcome back {this.props.userName}</span>
                  <MatchMakeBox/>
                </div>
        )
    }
})

var LoginButton = React.createClass({
    render: function() {
            return (
                    <div id="login_button" onClick={this.onClick}>Log In</div>
            )
    },
    onClick: function() {
        window.location.replace(baseUrl + "/login") 
    }
})

var ContentBox = React.createClass({
    getInitialState: function() {
        return {"isLoggedIn": typeof userName != 'undefined'}
    },
    render: function() {
        if (this.state.isLoggedIn) {
            return (<LoggedInBox userName={userName}/>)
        } else {
            return (<LoginBox/>)
        }
    }
})

ReactDOM.render(
  <ContentBox/>,
  document.getElementById('content')
);
