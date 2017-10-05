AJS.$(function (/**jQuery*/$) {
    $('body').bind('ajaxComplete', function (e, x) {
        if (x.responseText && /user\-hover\-info/.test(x.responseText)) {
            $('.user-hover-info').each(function () {
                var $vcard = $(this);
                var email = $vcard.find('h5 a').text();
                if (!$vcard.hasClass('hc-status-applied')) {
                    $.get($('meta[name="ajs-context-path"]').attr('content')
                        + '/plugins/servlet/hipchatproxy/v1/users/show', {user_id:email}, function (d) {
                        $vcard.addClass('hc-status-applied');
                        $vcard.find('h4').append('<a class="hc-status ' + d.user.status.toLowerCase() + '" title="'
                            + d.user.status + ' on HipChat"><span>'
                            + d.user.status + '</span></a>');
                    }, 'json');
                }
            });

        }
    });

});