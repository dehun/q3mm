var baseUrl = "http://hurtmeplenty.space:9000"

var LoginBox = React.createClass({
    render: function() {
            return (
            <div id="login_box">
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
        return (<div id="mm_state" className="state_box">
                <div>{this.props.state}</div>
                <div><img src="assets/images/loader.gif"/></div>
                </div>)
    }
})

var MatchMakeFail = React.createClass({
    render: function() {
        return (<div id="mm_fail" className="state_box">Fail: {this.props.reason}</div>)
    }
})

var ConnectToServer = React.createClass({
    render: function() {
        setTimeout(function() {
            window.location.replace(this.props.server)
        }, 3000)
        return (
                <div id="connect_to_server" className="state_box">
                    <a href={this.props.server} className="server_link">{this.props.server}</a>
                </div>)
    }
})
 
var MatchMakeButton = React.createClass({
    render: function() {
        return (<button id="match_make_button" className="big_button" onClick={this.onClick}>Start match</button>)
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
        var msg = JSON.parse(msgs.data)
        switch (msg.cmd) {
        case "enqueued":
            this.setState({"phase": "enqueued"})
            break
        case "newChallenge":
            this.setState({"phase": "newChallenge", "server": msg.body.server})
            break
        case "noCompetition":
            this.setState({"phase": "fail", "reason": msg.reason})
            break
        }
    },
    render: function() {
        switch (this.state.phase) {
        case "idle":
            return (<MatchMakeButton onWsConnecting={this.onWsConnecting}
                    onWsOpen={this.onWsOpen} onWsClosed={this.onWsClosed} onWsMessage={this.onWsMessage}/>)
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
        return (<button id="on_demand_start_button" className="big_button" onClick={this.onClick}>Create server</button>)
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

var Rules = React.createClass({
    render: function() {
        return (<div id="rules">
                <h3>Rules</h3>
                <ul id="rules_list">
                <li>Servers are deleted on inactivity, disconnects</li>
                <li>Duels only</li>
                <li>Glickos at the moment grabbed from <a href="qlstats.net">qlstats.net</a></li>
                </ul>
                </div>)
    }
})

var LoggedInBox = React.createClass({
    render: function() {
        return (
                <div id="logged_in_box">
                  <div id="welcome">
                    <div>You are logged in as </div>
                    <div id="welcome_avatar"><img src={this.props.userAvatar}/></div>
                    <div id="welcome_nick">{this.props.userName}</div>
                  </div>
                  <Rules/>
                  <MatchMakeBox/>
                </div>)
    }
})

var LoginButton = React.createClass({
    render: function() {
            return (
                    <button id="login_button" className="big_button" onClick={this.onClick}>Log In</button>
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
            return (<div id="react_top_box"><LoggedInBox userName={userName} userAvatar={userAvatar}/></div>)
        } else {
            return (<div id="react_top_box"><LoginBox/></div>)
        }
    }
})

ReactDOM.render(
  <ContentBox/>,
  document.getElementById('content')
);
